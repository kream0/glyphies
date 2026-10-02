package app.glyphies.sense

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import app.glyphies.data.Facing
import app.glyphies.engine.InputFrame
import app.glyphies.engine.Need
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The phone's senses, turned into [InputFrame]s: tilt (gravity), shakes, the microphone's
 * loudness and claps, light, proximity and the compass, plus the "buttons" (screen taps and
 * volume keys). Sensors only run while something that needs them plays.
 */
class Sensors(private val context: Context) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val accelSensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gravitySensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val lightSensor = manager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val proximitySensor = manager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val rotationSensor = manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    val mic = MicMeter(context)

    fun has(need: Need): Boolean = when (need) {
        Need.TILT -> accelSensor != null
        Need.MIC -> true
        Need.LIGHT -> lightSensor != null
        Need.PROXIMITY -> proximitySensor != null
        Need.COMPASS -> rotationSensor != null
    }

    // Gravity in device axes (m/s²): x to the right of the screen, y to its top, z out of it.
    /** When gravity last came in, to tell whether tilt data is live. */
    @Volatile private var tiltAt = 0L

    /** True while the tilt sensors are delivering (they may not in the background). */
    fun tiltLive(maxAgeMs: Long = 1500): Boolean = SystemClock.elapsedRealtime() - tiltAt < maxAgeMs

    @Volatile private var gx = 0f
    @Volatile private var gy = SensorManager.GRAVITY_EARTH
    @Volatile private var gz = 0f
    private var fusedGravity = false
    private var lastShake = 0L
    private val shakes = AtomicInteger()

    @Volatile private var lux = 100f
    @Volatile private var near = false
    @Volatile private var heading = 0f
    private val rotation = FloatArray(9)

    private val presses = AtomicInteger()
    private val volumeUp = AtomicInteger()
    private val volumeDown = AtomicInteger()
    @Volatile private var touchDown = false
    @Volatile private var keyDown = false
    @Volatile private var touchX: Float? = null

    private var running: Set<Need> = emptySet()
    private var facing = Facing.BACK

    fun start(needs: Set<Need>, facing: Facing, micBoostDb: Float) {
        stop()
        this.facing = facing
        val m = manager
        if (m != null) {
            if (Need.TILT in needs) {
                accelSensor?.let { m.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
                gravitySensor?.let { m.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            }
            if (Need.LIGHT in needs) lightSensor?.let { m.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            if (Need.PROXIMITY in needs) proximitySensor?.let { m.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            if (Need.COMPASS in needs) rotationSensor?.let { m.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }
        if (Need.MIC in needs) mic.start(micBoostDb)
        running = needs
        clearEvents()
    }

    fun stop() {
        manager?.unregisterListener(this)
        mic.stop()
        running = emptySet()
        fusedGravity = false
        touchDown = false
        keyDown = false
        touchX = null
    }

    private fun clearEvents() {
        shakes.set(0)
        presses.set(0)
        volumeUp.set(0)
        volumeDown.set(0)
        mic.takeClaps()
    }

    // ------------------------------------------------------------------ buttons

    fun tap() {
        presses.incrementAndGet()
    }

    fun touch(down: Boolean, x: Float?) {
        touchDown = down
        touchX = if (down) x else null
    }

    fun touchMove(x: Float) {
        if (touchDown) touchX = x
    }

    /** A volume key went down ([pressed]) or up; [plus] tells volume-up from volume-down. */
    fun volumeKey(plus: Boolean, pressed: Boolean) {
        if (pressed) {
            presses.incrementAndGet()
            if (plus) volumeUp.incrementAndGet() else volumeDown.incrementAndGet()
        }
        keyDown = pressed
    }

    // ------------------------------------------------------------------ snapshot

    /** This frame's input in matrix coordinates; counted events (taps, shakes, claps) are consumed. */
    fun snapshot(): InputFrame {
        val g = SensorManager.GRAVITY_EARTH
        // Seen from the back, the screen's right is on your left.
        val sx = if (facing == Facing.BACK) 1f else -1f
        return InputFrame(
            gx = (sx * gx / g).coerceIn(-1.5f, 1.5f),
            gy = (gy / g).coerceIn(-1.5f, 1.5f),
            gz = (gz / g).coerceIn(-1.5f, 1.5f),
            mic = mic.level,
            claps = mic.takeClaps(),
            shakes = shakes.getAndSet(0),
            presses = presses.getAndSet(0),
            volumeUp = volumeUp.getAndSet(0),
            volumeDown = volumeDown.getAndSet(0),
            held = touchDown || keyDown,
            touchX = touchX?.let { if (facing == Facing.BACK) 1f - it else it },
            light = (log10(lux + 1f) / 4f).coerceIn(0f, 1f),
            near = near,
            heading = heading,
        )
    }

    // ------------------------------------------------------------------ SensorEventListener

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                tiltAt = SystemClock.elapsedRealtime()
                fusedGravity = true
                gx = v[0]
                gy = v[1]
                gz = v[2]
            }
            Sensor.TYPE_ACCELEROMETER -> {
                tiltAt = SystemClock.elapsedRealtime()
                if (!fusedGravity) { // low-pass the raw acceleration into gravity
                    gx += (v[0] - gx) * 0.2f
                    gy += (v[1] - gy) * 0.2f
                    gz += (v[2] - gz) * 0.2f
                }
                val lx = v[0] - gx
                val ly = v[1] - gy
                val lz = v[2] - gz
                val jolt = sqrt(lx * lx + ly * ly + lz * lz)
                val now = SystemClock.elapsedRealtime()
                if (jolt > SHAKE_MS2 && now - lastShake > SHAKE_GAP_MS) {
                    lastShake = now
                    shakes.incrementAndGet()
                }
            }
            Sensor.TYPE_LIGHT -> lux = v[0]
            Sensor.TYPE_PROXIMITY -> {
                val range = event.sensor.maximumRange
                near = v[0] < minOf(range, 3f) || (range <= 1f && v[0] < range)
            }
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotation, v)
                heading = headingOf(rotation)
            }
        }
    }

    /**
     * Which way you face, in degrees from north. Standing the phone up, you look through it:
     * along the back-to-screen axis when you watch the back, the other way when you watch the
     * screen. Lying flat, it's the direction of the phone's top.
     */
    private fun headingOf(r: FloatArray): Float {
        // Columns of r are the device axes in world coordinates (east, north, up).
        val flat = abs(r[8]) > 0.8f
        val deg = if (flat) {
            Math.toDegrees(atan2(r[1], r[4]).toDouble())
        } else {
            val s = if (facing == Facing.BACK) 1f else -1f
            Math.toDegrees(atan2(s * r[2], s * r[5]).toDouble())
        }
        return ((deg + 360.0) % 360.0).toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        /** Linear acceleration (beyond gravity) that counts as a shake. */
        const val SHAKE_MS2 = 13f
        const val SHAKE_GAP_MS = 350L
    }
}

/**
 * Loudness at the microphone, 0 (quiet room) .. 1 (shouting), plus "claps": sudden loud
 * sounds. Nothing is recorded or kept; levels are computed on the fly, 20 ms at a time.
 */
class MicMeter(private val context: Context) {
    @Volatile var level = 0f
        private set

    private val claps = AtomicInteger()
    @Volatile private var running = false
    private var worker: Thread? = null

    val permitted: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** True while the microphone is actually being read. */
    @Volatile var live = false
        private set

    fun takeClaps(): Int = claps.getAndSet(0)

    @SuppressLint("MissingPermission")
    fun start(boostDb: Float) {
        if (running || !permitted) return
        running = true
        worker = thread(name = "glyphies-mic", isDaemon = true) {
            val rate = 16_000
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val record = try {
                AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(min, rate / 5))
            } catch (e: Exception) {
                null
            }
            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                record?.release()
                running = false
                return@thread
            }
            val buffer = ShortArray(rate / 50)
            var smooth = 0f
            var base = 0f
            var lastClap = 0L
            try {
                record.startRecording()
                live = true
                while (running) {
                    val n = record.read(buffer, 0, buffer.size)
                    if (n <= 0) continue
                    var sum = 0.0
                    for (i in 0 until n) {
                        val s = buffer[i] / 32768.0
                        sum += s * s
                    }
                    val db = 20.0 * log10(sqrt(sum / n) + 1e-9) + boostDb
                    val raw = ((db + 55.0) / 40.0).toFloat().coerceIn(0f, 1f) // -55 dBFS → 0, -15 dBFS → 1
                    smooth += (raw - smooth) * if (raw > smooth) 0.6f else 0.12f
                    level = smooth
                    val now = SystemClock.elapsedRealtime()
                    if (raw > 0.55f && raw - base > 0.3f && now - lastClap > 220) {
                        lastClap = now
                        claps.incrementAndGet()
                    }
                    base += (raw - base) * 0.08f
                }
            } catch (e: Exception) {
                // Microphone taken by another app, or permission revoked: just go quiet.
            } finally {
                live = false
                level = 0f
                runCatching { record.stop() }
                record.release()
            }
        }
    }

    fun stop() {
        running = false
        worker = null
        level = 0f
    }
}

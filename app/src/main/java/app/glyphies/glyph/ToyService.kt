package app.glyphies.glyph

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import app.glyphies.Graph
import app.glyphies.data.Facing
import app.glyphies.engine.HourglassToy
import app.glyphies.engine.InputFrame
import app.glyphies.engine.Need
import app.glyphies.engine.NoFx
import app.glyphies.engine.Playable
import app.glyphies.play.FaceDown
import app.glyphies.sense.Sensors
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * "Glyphies" Glyph Toy: plays what you picked in the app (⋯ › Show face down) on the Glyph
 * Matrix with the app closed — the analog clock unless you chose sand, the hourglass or one of
 * your drawings or animations.
 *
 * - Phone (4a) Pro: always-on toys only. Settings › Glyph Interface › Flip to Glyph ›
 *   Always-on Glyph Toy › Glyphies: it shows when the phone lies face down.
 * - Phone (3): add it to the Glyph Button carousel; a long press restarts it.
 *
 * Sand and the hourglass read the tilt when Android lets a toy use the accelerometer; lying
 * flat (or without sensor data) they get a slow, gentle sway so the sand keeps moving.
 */
class ToyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: GlyphSession? = null
    private val jobs = ArrayList<Job>()
    private var playable: Playable? = null
    private var sensors: Sensors? = null

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) {
                super.handleMessage(msg)
                return
            }
            val event = msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA) ?: return
            GlyphLink.toyEvent(event)
            when (event) {
                GlyphToy.EVENT_CHANGE -> playable?.restart() // long press on the Glyph Button
                GlyphToy.EVENT_AOD -> session?.refresh() // always-on wake-up: repaint now
            }
        }
    }
    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder? {
        if (GlyphSupport.isSupported && jobs.isEmpty()) {
            val s = GlyphSession(this, toyMode = true).also { it.open() }
            session = s
            val device = MatrixShape.forSize(GlyphSupport.matrixSize)
            jobs += scope.launch {
                // Rebuild whenever the choice (or the chosen creation) changes.
                combine(
                    Graph.settings.state.map { it.toy ?: FaceDown.DEFAULT }.distinctUntilChanged(),
                    Graph.creations.all,
                ) { key, all -> key to all.firstOrNull { it.id == key }?.updated }
                    .distinctUntilChanged()
                    .collect { (key, _) -> rebuild(key, device) }
            }
            jobs += scope.launch {
                var last = SystemClock.elapsedRealtime()
                var t = 0f
                var doneFor = 0f
                while (isActive) {
                    val p = playable
                    val now = SystemClock.elapsedRealtime()
                    val dt = ((now - last) / 1000f).coerceIn(0f, 0.5f)
                    last = now
                    t += dt
                    if (p != null) {
                        p.update(dt, input(t), NoFx)
                        // A face-down hourglass turns itself over a few seconds after it runs out.
                        if (p is HourglassToy && p.finished) {
                            doneFor += dt
                            if (doneFor > 4f) {
                                p.restart()
                                doneFor = 0f
                            }
                        } else {
                            doneFor = 0f
                        }
                        val frame = p.shape.blank()
                        p.render(frame)
                        val fitted = device.mask(Frames.fit(frame, p.shape.width, p.shape.height, device.width, device.height))
                        // Face down the system dims the matrix, so the toy uses the service's
                        // full range (the app channel keeps 0..255, which reads well up close).
                        s.show(Frames.toRaw(fitted, Graph.settings.current.faceDownBrightness))
                    }
                    delay((p?.frameMs ?: 200L).coerceIn(60L, 250L))
                }
            }
        }
        return messenger.binder
    }

    private fun rebuild(key: String, device: MatrixShape) {
        val p = FaceDown.playable(key, device)
        playable = p
        if (Need.TILT in p.needs) {
            val sn = sensors ?: Sensors(applicationContext).also { sensors = it }
            sn.start(setOf(Need.TILT), Facing.BACK, 0f)
        } else {
            sensors?.stop()
        }
    }

    /** Real tilt when it's live and the phone isn't flat; otherwise a slow sway. */
    private fun input(t: Float): InputFrame {
        val real = sensors?.takeIf { it.tiltLive() }?.snapshot()
        if (real != null && hypot(real.gx, real.gy) > 0.35f) return real
        return InputFrame(
            gx = 0.55f * sin(t * 2f * PI.toFloat() / 18f),
            gy = 0.8f + 0.15f * cos(t * 2f * PI.toFloat() / 11f),
            gz = real?.gz ?: 0f,
        )
    }

    override fun onUnbind(intent: Intent?): Boolean {
        jobs.forEach { it.cancel() }
        jobs.clear()
        playable = null
        sensors?.stop()
        session?.close()
        session = null
        return false
    }

    override fun onDestroy() {
        sensors?.stop()
        scope.cancel()
        super.onDestroy()
    }
}

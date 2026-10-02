package app.glyphies.engine

import app.glyphies.glyph.MatrixShape
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** How fast the sand runs: physics steps per second. */
private const val STEPS_PER_SECOND = 60f

/** Runs a [SandSim] at a steady step rate whatever the frame rate. */
private class Stepper {
    private var acc = 0f

    fun steps(dt: Float): Int {
        acc += dt.coerceAtMost(0.1f) * STEPS_PER_SECOND
        val n = acc.toInt()
        acc -= n
        return n.coerceAtMost(6)
    }
}

/**
 * Sand: grains fill part of the matrix and follow the phone's tilt. Shake to throw them
 * about; volume up / down (or + / − on screen) adds or removes sand.
 */
class SandToy(
    override val shape: MatrixShape,
    fill: Float = 0.42f,
    private val textured: Boolean = true,
    random: Random = Random.Default,
) : Playable {
    override val id: String = "sand"
    override val needs: Set<Need> = setOf(Need.TILT)

    private val sim = SandSim(shape, random = random)
    private val stepper = Stepper()
    private val initial = (sim.capacity * fill).roundToInt()

    init {
        sim.sprinkle(initial, textured)
    }

    val grains: Int get() = sim.count
    val capacity: Int get() = sim.capacity

    override fun update(dt: Float, input: InputFrame, fx: Fx) {
        repeat(input.shakes.coerceAtMost(2)) { sim.shake(0.9f) }
        if (input.presses > input.volumeUp + input.volumeDown) sim.shake(0.5f) // a tap stirs the sand
        if (input.volumeUp > 0) more()
        if (input.volumeDown > 0) less()
        repeat(stepper.steps(dt)) { sim.step(input.gx, input.gy, input.gz) }
    }

    /** Adds a handful of grains at the top (relative to gravity, they'll fall). */
    fun more(n: Int = maxOf(4, sim.capacity / 20)) {
        sim.sprinkle(n, textured)
    }

    fun less(n: Int = maxOf(4, sim.capacity / 20)) {
        repeat(n) { if (sim.count > 1) sim.removeLast() }
    }

    override fun render(out: IntArray) = sim.render(out)

    override fun hud(): Hud = Hud(detail = "${sim.count} / ${sim.capacity}")

    override fun restart() {
        sim.clear()
        sim.sprinkle(initial, textured)
    }
}

/**
 * Hourglass timer: the glass is drawn faintly, the sand starts in the top bulb and one grain
 * drops through the neck at a time, so the top empties in [seconds]. Turn the phone over and
 * it runs back; lay it on its side and it pauses, like the real thing. A volume key refills it.
 * Buzzes when the time is up.
 */
class HourglassToy(
    override val shape: MatrixShape,
    val seconds: Int = 60,
    private val random: Random = Random.Default,
) : Playable {
    override val id: String = "hourglass"
    override val needs: Set<Need> = setOf(Need.TILT)

    private val interior: BooleanArray = hourglassInterior(shape)
    private val outline: BooleanArray = BooleanArray(shape.cells) { i ->
        if (interior[i] || !shape.visible[i]) return@BooleanArray false
        val x = i % shape.width
        val y = i / shape.width
        (-1..1).any { dy -> (-1..1).any { dx -> shape.inside(x + dx, y + dy) && interior[(y + dy) * shape.width + x + dx] } }
    }
    private val neckRow: Int = shape.height / 2
    private val neckX: Int = (shape.width - 1) / 2
    private lateinit var sim: SandSim
    private val stepper = Stepper()
    private var total = 1
    private var interval = 1f
    private var dropT = 0f
    private var flash = 0f
    private var done = false
    private var lastDown = true

    init {
        fillTop()
    }

    private fun fillTop() {
        // The neck is solid for the physics; grains are passed through it one at a time.
        val walls = BooleanArray(shape.cells) { !interior[it] || it == neckRow * shape.width + neckX }
        sim = SandSim(shape, walls = walls, random = random).apply {
            elasticity = 0.3f
            accel = 0.16f
        }
        // Fill the top bulb, bottom rows first, leaving its top couple of rows empty.
        val top = (0 until shape.cells).filter { interior[it] && it / shape.width < neckRow }
        val keep = (top.size * 0.82f).roundToInt()
        top.sortedByDescending { it / shape.width }.take(keep).forEach {
            sim.add(it % shape.width, it / shape.width, sim.grainLevel())
        }
        total = sim.count.coerceAtLeast(1)
        interval = seconds.toFloat() / total
        dropT = interval
        done = false
    }

    /** Grains in the bulb that's currently on top. */
    private fun upper(down: Boolean): Int = sim.cells().count { if (down) it / shape.width < neckRow else it / shape.width > neckRow }

    override fun update(dt: Float, input: InputFrame, fx: Fx) {
        // Only a volume key refills it: fingers resting on the screen while you watch the back
        // shouldn't reset a running timer.
        if (input.volumeUp + input.volumeDown > 0) fillTop()
        repeat(stepper.steps(dt)) { sim.step(input.gx, input.gy, input.gz) }
        flash -= dt
        // Upright (either way up) and a grain waiting at the neck: let one through.
        val down = input.gy > 0.35f
        val up = input.gy < -0.35f
        if (down || up) {
            lastDown = down
            dropT -= dt
            if (dropT <= 0f) {
                val from = if (down) neckRow - 1 else neckRow + 1
                val to = if (down) neckRow + 1 else neckRow - 1
                if (sim.transfer(neckX, from, neckX, to)) {
                    flash = 0.08f
                    dropT += interval
                } else {
                    dropT = 0f // ready as soon as a grain gets there
                }
            }
            val left = upper(down)
            if (left == 0 && !done) {
                done = true
                fx.buzz(Buzz.WIN)
            } else if (left > 0) {
                done = false
            }
        }
    }

    override fun render(out: IntArray) {
        for (i in outline.indices) if (outline[i]) out[i] = OUTLINE
        sim.render(out)
        if (flash > 0f) out[neckRow * shape.width + neckX] = 200
    }

    /** Time left for the grains still on top (as the glass stands now). */
    override fun hud(): Hud {
        val left = upper(lastDown)
        val secs = (left * interval).roundToInt()
        return Hud(detail = "%d:%02d".format(secs / 60, secs % 60))
    }

    override fun restart() = fillTop()

    companion object {
        private const val OUTLINE = 34
        /** The inside of the glass: two bulbs meeting at a one-dot neck in the middle row. */
        fun hourglassInterior(shape: MatrixShape): BooleanArray {
            val n = shape.height
            val mid = n / 2
            val top = 1
            val maxHalf = (shape.width - 1) / 2 - (if (n >= 20) 3 else 3)
            val inside = BooleanArray(shape.cells)
            val c = (shape.width - 1) / 2
            for (y in top until n - top) {
                val d = abs(y - mid) // 0 at the neck
                val reach = mid - top
                // Straight-sided bulbs that open up quickly from the neck, then stay wide.
                val half = ((d.toFloat() / reach) * maxHalf * 1.5f).roundToInt().coerceAtMost(maxHalf)
                for (x in c - half..c + half) {
                    if (shape.isLed(x, y) && shape.isLed(x - 1, y) || shape.isLed(x, y) && half == 0) {
                        inside[y * shape.width + x] = true
                    }
                }
            }
            // Keep a solid rim of LEDs around the glass.
            for (y in 0 until n) for (x in 0 until shape.width) {
                val i = y * shape.width + x
                if (inside[i] && (!shape.isLed(x - 1, y) || !shape.isLed(x + 1, y) || !shape.isLed(x, y - 1) || !shape.isLed(x, y + 1))) {
                    inside[i] = false
                }
            }
            return inside
        }
    }
}

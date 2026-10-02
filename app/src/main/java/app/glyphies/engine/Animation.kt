package app.glyphies.engine

import app.glyphies.glyph.DotText
import app.glyphies.glyph.MatrixShape
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** How an animation goes round. */
enum class LoopMode { LOOP, BOUNCE, ONCE }

/** What picks the frame of an animation: the clock, or one of the phone's sensors. */
enum class Driver(val needs: Set<Need>) {
    /** Plays at its frame rate. */
    TIME(emptySet()),

    /** Louder = later frame (a mouth, a VU meter…). */
    MIC(setOf(Need.MIC)),

    /** Tilting left → right walks through the frames. */
    TILT_X(setOf(Need.TILT)),

    /** Tilting forward → back walks through the frames. */
    TILT_Y(setOf(Need.TILT)),

    /** Turning around: the frames go round with the compass (an arrow, a dial). */
    COMPASS(setOf(Need.COMPASS)),

    /** Darker → brighter room walks through the frames. */
    LIGHT(setOf(Need.LIGHT)),

    /** First frame normally, the rest plays while a hand covers the screen. */
    PROXIMITY(setOf(Need.PROXIMITY)),

    /** Each shake shows the next frame. */
    SHAKE(setOf(Need.TILT)),

    /** Each tap or volume key shows the next frame. */
    TAP(emptySet()),

    /** Each clap (or loud sound) shows the next frame. */
    CLAP(setOf(Need.MIC)),

    /** Shake and the drawing crumbles into sand that follows the tilt; tap to rebuild it. */
    SAND(setOf(Need.TILT)),
}

/** Plays a creation's frames on the matrix, driven by [driver]. */
class AnimationPlayer(
    override val shape: MatrixShape,
    frames: List<IntArray>,
    private val fps: Int = 8,
    private val loop: LoopMode = LoopMode.LOOP,
    private val driver: Driver = Driver.TIME,
    override val id: String = "animation",
    private val random: Random = Random.Default,
) : Playable {
    override val needs: Set<Need> = driver.needs

    private val frames: List<IntArray> = frames.map { Marks.strip(it) }.ifEmpty { listOf(shape.blank()) }
    private val n = this.frames.size
    private var clock = 0f
    private var index = 0
    private var smooth = 0f
    private var sand: SandSim? = null
    private var crumbled = false

    init {
        if (driver == Driver.SAND) buildSand()
    }

    val currentFrame: Int get() = index

    private fun buildSand() {
        val sim = SandSim(shape, random = random)
        val f = frames[0]
        // Bottom-most dots first, so the drawing stays put until it's tipped.
        val order = (0 until shape.cells).filter { f[it] > 0 }.sortedByDescending { it / shape.width }
        for (i in order) sim.add(i % shape.width, i / shape.width, f[i])
        sand = sim
        crumbled = false
    }

    override fun restart() {
        clock = 0f
        index = 0
        if (driver == Driver.SAND) buildSand()
    }

    override fun update(dt: Float, input: InputFrame, fx: Fx) {
        clock += dt
        when (driver) {
            Driver.TIME -> index = timeIndex()
            Driver.MIC -> index = level(input.mic, 0.25f)
            Driver.TILT_X -> index = level(((input.gx / 0.6f).coerceIn(-1f, 1f) + 1f) / 2f, 0.35f)
            Driver.TILT_Y -> index = level(((input.gy / 0.6f).coerceIn(-1f, 1f) + 1f) / 2f, 0.35f)
            Driver.LIGHT -> index = level(input.light, 0.1f)
            Driver.COMPASS -> index = ((input.heading / 360f) * n).roundToInt().mod(n)
            Driver.PROXIMITY -> {
                if (input.near && n > 1) {
                    index = if (n == 2) 1 else 1 + (clock * fps).toInt() % (n - 1)
                } else {
                    index = 0
                    clock = 0f
                }
            }
            Driver.SHAKE -> if (input.shakes > 0) next()
            Driver.TAP -> if (input.presses > 0) next()
            Driver.CLAP -> if (input.claps > 0 || input.presses > 0) next()
            Driver.SAND -> {
                if (input.presses > 0) {
                    buildSand() // back together
                    return
                }
                val sim = sand ?: return
                if (input.shakes > 0) {
                    crumbled = true
                    sim.shake(0.5f)
                }
                if (!crumbled) return // the drawing holds until the first shake
                val steps = (dt * 60f).roundToInt().coerceIn(1, 4)
                repeat(steps) { sim.step(input.gx, input.gy, input.gz) }
            }
        }
    }

    private fun next() {
        index = when (loop) {
            LoopMode.ONCE -> (index + 1).coerceAtMost(n - 1)
            else -> (index + 1) % n
        }
    }

    /** Maps a 0..1 value onto the frames, smoothed so a noisy sensor doesn't flicker. */
    private fun level(v: Float, ease: Float): Int {
        smooth += (v.coerceIn(0f, 1f) - smooth) * ease
        return (smooth * (n - 1)).roundToInt().coerceIn(0, n - 1)
    }

    private fun timeIndex(): Int {
        if (n == 1) return 0
        val k = (clock * fps.coerceIn(1, 60)).toInt()
        return when (loop) {
            LoopMode.LOOP -> k % n
            LoopMode.ONCE -> k.coerceAtMost(n - 1)
            LoopMode.BOUNCE -> {
                val period = 2 * (n - 1)
                val p = k % period
                if (p < n) p else period - p
            }
        }
    }

    override fun render(out: IntArray) {
        val sim = sand
        if (sim != null) {
            sim.render(out)
            return
        }
        val f = frames[index.coerceIn(0, n - 1)]
        for (i in out.indices) out[i] = if (i < f.size && shape.visible[i]) f[i] else 0
    }

    override fun hud(): Hud = if (n > 1 && driver != Driver.SAND) Hud(detail = "${index + 1} / $n") else Hud.NONE

    companion object {
        /** Frames that scroll [text] across the matrix (for the "Text" creation). */
        fun scrollingText(shape: MatrixShape, text: String): List<IntArray> {
            val strip = DotText.layout(text)
            val total = strip.width + shape.width + 2
            return List(total.coerceAtLeast(1)) { k ->
                val out = shape.blank()
                Canvas(shape, out).strip(strip, shape.width - k, DotText.centredTop(shape.height), 255)
                out
            }
        }
    }
}

/** The difference between two frames, for tests and thumbnails. */
fun frameDistance(a: IntArray, b: IntArray): Int {
    var d = 0
    for (i in a.indices) d += abs(a[i] - b.getOrElse(i) { 0 })
    return d
}

package app.glyphies.engine

import app.glyphies.glyph.MatrixShape

/** What the phone senses this frame, already turned into matrix coordinates (x right, y down). */
data class InputFrame(
    /** Gravity along the matrix in g: (0, 1) when the phone stands upright, ~0 when lying flat. */
    val gx: Float = 0f,
    val gy: Float = 1f,
    /** Gravity across the matrix (towards or away from the viewer), in g. */
    val gz: Float = 0f,
    /** Smoothed loudness at the microphone, 0 (silence) .. 1 (shouting). */
    val mic: Float = 0f,
    /** Loud sounds (claps, "ha!") since the last frame. */
    val claps: Int = 0,
    /** Shakes since the last frame. */
    val shakes: Int = 0,
    /** Action presses since the last frame: screen taps and volume keys. */
    val presses: Int = 0,
    val volumeUp: Int = 0,
    val volumeDown: Int = 0,
    /** A finger is down on the screen (or a volume key is held). */
    val held: Boolean = false,
    /** Where the finger is on the screen, 0..1 from the left, or null. */
    val touchX: Float? = null,
    /** Ambient light, 0 (dark) .. 1 (daylight), on a log scale. */
    val light: Float = 0.5f,
    /** Something covers the proximity sensor. */
    val near: Boolean = false,
    /** Compass heading in degrees, 0 = north. */
    val heading: Float = 0f,
)

/** Phone features a playable reads; the app only switches on the sensors that are needed. */
enum class Need { TILT, MIC, LIGHT, PROXIMITY, COMPASS }

enum class Buzz { TICK, HIT, BIG, WIN }

/** Side effects a playable can ask for. */
interface Fx {
    fun buzz(kind: Buzz)

    /** Called once when a game ends, with its final score (high scores are kept by the app). */
    fun gameOver(score: Int)
}

object NoFx : Fx {
    override fun buzz(kind: Buzz) = Unit
    override fun gameOver(score: Int) = Unit
}

/** Shown on the phone screen next to the matrix. */
data class Hud(
    val score: Int? = null,
    val lives: Int? = null,
    val level: Int? = null,
    /** A short status such as a grain count or a time. */
    val detail: String? = null,
    val over: Boolean = false,
) {
    companion object {
        val NONE = Hud()
    }
}

/** Something that runs on the Glyph Matrix: an animation, a toy or a game. */
interface Playable {
    /** Stable key, used for high scores. */
    val id: String
    val shape: MatrixShape
    val needs: Set<Need> get() = emptySet()

    /** Preferred time between frames. */
    val frameMs: Long get() = 33L

    /** Advances by [dt] seconds. */
    fun update(dt: Float, input: InputFrame, fx: Fx)

    /** Draws the current picture into [out] (already cleared, shape.cells long). */
    fun render(out: IntArray)

    fun hud(): Hud = Hud.NONE

    fun restart() {}
}

/** Words the games show on the matrix, in the app's language. */
data class Labels(
    val gameOver: String = "GAME OVER",
    val win: String = "WIN",
    val tapToPlay: String = "TAP TO PLAY",
)

/** How a player moves along one axis. */
enum class Move { TILT, TOUCH, MIC }

/** What fires, flaps or launches. */
enum class Trigger { AUTO, TAP, CLAP, SHAKE, MIC }

data class GameConfig(
    val move: Move = Move.TILT,
    val trigger: Trigger = Trigger.TAP,
    /** 1 (calm) .. 5 (frantic). */
    val speed: Int = 3,
    val lives: Int = 3,
    /** Sideways gravity (in g) that moves the player all the way to an edge. */
    val tiltRange: Float = 0.42f,
    val labels: Labels = Labels(),
) {
    /** 0.7 .. 1.5, the multiplier most speeds use. */
    val pace: Float get() = 0.5f + 0.2f * speed.coerceIn(1, 5)

    /** True when this frame's input fires the trigger. */
    fun fired(input: InputFrame): Boolean = when (trigger) {
        Trigger.AUTO -> true
        Trigger.TAP -> input.presses > 0
        Trigger.CLAP -> input.claps > 0 || input.presses > 0
        Trigger.SHAKE -> input.shakes > 0 || input.presses > 0
        Trigger.MIC -> input.mic > 0.45f || input.presses > 0
    }

    val needs: Set<Need>
        get() = buildSet {
            if (move == Move.TILT) add(Need.TILT)
            if (move == Move.MIC || trigger == Trigger.CLAP || trigger == Trigger.MIC) add(Need.MIC)
            if (trigger == Trigger.SHAKE) add(Need.TILT)
        }
}

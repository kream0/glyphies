package app.glyphies.engine

import app.glyphies.glyph.MatrixShape
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * An analog clock on the round matrix: twelve hour marks on the rim (brighter at 12, 3, 6, 9),
 * a short bright hour hand, a longer minute hand that creeps with the seconds, and a seconds
 * dot running round the rim. The hands are anti-aliased: each LED lights by how close it is to
 * the hand, so they read as lines even on 13 × 13 dots. Tap to hide or show the seconds.
 */
class AnalogClock(
    override val shape: MatrixShape,
    private val now: () -> LocalTime = { LocalTime.now() },
) : Playable {
    override val id: String = "clock"
    override val frameMs: Long = 200L

    private val big = shape.width >= 20
    private val cx = (shape.width - 1) / 2f
    private val cy = (shape.height - 1) / 2f
    private val radius = shape.width / 2f - 0.5f

    /** LED index on the rim for each of the 60 minute positions. */
    private val rim = IntArray(60) { k -> rimCell(k * 6.0) }
    private var seconds = true

    private fun rimCell(degrees: Double): Int {
        val a = degrees * PI / 180.0
        var r = radius.toDouble()
        while (r > 1.0) {
            val x = (cx + sin(a) * r).roundToInt()
            val y = (cy - cos(a) * r).roundToInt()
            if (shape.isLed(x, y)) return y * shape.width + x
            r -= 0.5
        }
        return (cy.roundToInt()) * shape.width + cx.roundToInt()
    }

    override fun update(dt: Float, input: InputFrame, fx: Fx) {
        if (input.presses > 0) seconds = !seconds
    }

    override fun render(out: IntArray) {
        val t = now()
        val s = t.second + t.nano / 1e9
        val m = t.minute + s / 60.0
        val h = (t.hour % 12) + m / 60.0

        // Hour marks
        for (k in 0 until 12) {
            val i = rim[k * 5]
            out[i] = max(out[i], if (k % 3 == 0) 130 else 45)
        }
        // Hands: minute first, the hour hand on top.
        hand(out, m / 60.0 * 360.0, radius - if (big) 1.6f else 1.2f, if (big) 0.62f else 0.5f, 200)
        hand(out, h / 12.0 * 360.0, radius * 0.56f, if (big) 0.95f else 0.62f, 255)
        // Seconds on the rim
        if (seconds) {
            val i = rim[t.second % 60]
            out[i] = max(out[i], 170)
        }
        val c = cy.roundToInt() * shape.width + cx.roundToInt()
        out[c] = 255
        shape.mask(out)
    }

    /** Lights the LEDs near the segment from the centre at [degrees] (12 o'clock = 0, clockwise). */
    private fun hand(out: IntArray, degrees: Double, length: Float, width: Float, level: Int) {
        val a = degrees * PI / 180.0
        val tx = (sin(a) * length).toFloat()
        val ty = (-cos(a) * length).toFloat()
        val len2 = tx * tx + ty * ty
        for (y in 0 until shape.height) for (x in 0 until shape.width) {
            if (!shape.isLed(x, y)) continue
            val px = x - cx
            val py = y - cy
            // Distance from the LED to the segment.
            val u = ((px * tx + py * ty) / len2).coerceIn(0f, 1f)
            val dx = px - u * tx
            val dy = py - u * ty
            val d = sqrt(dx * dx + dy * dy)
            val k = 1f - (d - width * 0.35f) / width
            if (k <= 0f) continue
            val v = (level * k.coerceAtMost(1f)).roundToInt()
            val i = y * shape.width + x
            if (v > out[i]) out[i] = v
        }
    }

    override fun hud(): Hud {
        val t = now()
        return Hud(detail = "%02d:%02d".format(t.hour, t.minute))
    }
}

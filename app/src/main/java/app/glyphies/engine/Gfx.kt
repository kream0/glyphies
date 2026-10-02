package app.glyphies.engine

import app.glyphies.glyph.DotStrip
import app.glyphies.glyph.DotText
import app.glyphies.glyph.MatrixShape
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** A small picture (brightness 0..255 per pixel), e.g. a ship, an invader or a ball. */
class Sprite(val w: Int, val h: Int, val px: IntArray) {
    init {
        require(px.size == w * h)
    }

    fun at(x: Int, y: Int): Int = if (x in 0 until w && y in 0 until h) px[y * w + x] else 0
    fun lit(x: Int, y: Int): Boolean = at(x, y) > 0
    val isEmpty: Boolean get() = px.none { it > 0 }

    /** The same picture without its empty border (a blank sprite becomes a single dot). */
    fun trimmed(): Sprite {
        var x0 = w
        var y0 = h
        var x1 = -1
        var y1 = -1
        for (y in 0 until h) for (x in 0 until w) if (px[y * w + x] > 0) {
            if (x < x0) x0 = x
            if (y < y0) y0 = y
            if (x > x1) x1 = x
            if (y > y1) y1 = y
        }
        if (x1 < 0) return DOT
        if (x0 == 0 && y0 == 0 && x1 == w - 1 && y1 == h - 1) return this
        val nw = x1 - x0 + 1
        val nh = y1 - y0 + 1
        return Sprite(nw, nh, IntArray(nw * nh) { i -> px[(y0 + i / nw) * w + x0 + i % nw] })
    }

    companion object {
        val DOT = Sprite(1, 1, intArrayOf(255))

        /** Rows of '#' (full), '+' (bright), '-' (dim), '.' (off). */
        fun parse(vararg rows: String): Sprite {
            val w = rows.maxOf { it.length }
            val h = rows.size
            val px = IntArray(w * h)
            rows.forEachIndexed { y, row ->
                row.forEachIndexed { x, c -> px[y * w + x] = level(c) }
            }
            return Sprite(w, h, px)
        }

        fun level(c: Char): Int = when (c) {
            '#' -> 255
            '+' -> 170
            '-' -> 90
            '*' -> 50
            else -> 0
        }
    }
}

/** Drawing on a frame of a [MatrixShape]; cells without an LED are skipped. */
class Canvas(val shape: MatrixShape, var out: IntArray) {
    val w: Int get() = shape.width
    val h: Int get() = shape.height

    fun plot(x: Int, y: Int, v: Int) {
        if (!shape.isLed(x, y)) return
        val i = y * shape.width + x
        if (v > out[i]) out[i] = v.coerceAtMost(255)
    }

    /** Draws [s] with its top-left corner at (x, y); [scale] multiplies its brightness. */
    fun sprite(s: Sprite, x: Int, y: Int, scale: Float = 1f) {
        for (sy in 0 until s.h) for (sx in 0 until s.w) {
            val v = s.px[sy * s.w + sx]
            if (v > 0) plot(x + sx, y + sy, (v * scale).roundToInt().coerceAtLeast(1))
        }
    }

    fun strip(strip: DotStrip, x: Int, top: Int, v: Int, scale: Int = 1) {
        for (c in 0 until strip.width) {
            val col = strip.columns[c]
            if (col == 0) continue
            for (row in 0 until 10) {
                if (col and (1 shl row) == 0) continue
                for (dy in 0 until scale) for (dx in 0 until scale) plot(x + c * scale + dx, top + row * scale + dy, v)
            }
        }
    }

    /** Text (or digits) centred on the matrix; doubles in size when there's room. */
    fun centred(text: String, v: Int = 255) {
        val strip = DotText.layout(text)
        val scale = if (strip.width * 2 <= w - 2 && h >= 20) 2 else 1
        strip(strip, (w - strip.width * scale) / 2, DotText.centredTop(h, scale), v, scale)
    }
}

/** A sprite placed in a frame: pixel-exact overlap tests. */
fun overlaps(a: Sprite, ax: Int, ay: Int, b: Sprite, bx: Int, by: Int): Boolean {
    val x0 = maxOf(ax, bx)
    val y0 = maxOf(ay, by)
    val x1 = minOf(ax + a.w, bx + b.w)
    val y1 = minOf(ay + a.h, by + b.h)
    for (y in y0 until y1) for (x in x0 until x1) {
        if (a.lit(x - ax, y - ay) && b.lit(x - bx, y - by)) return true
    }
    return false
}

/** True when every lit pixel of [s] at (x, y) lands on an LED. */
fun fits(shape: MatrixShape, s: Sprite, x: Int, y: Int): Boolean {
    for (sy in 0 until s.h) for (sx in 0 until s.w) {
        if (s.lit(sx, sy) && !shape.isLed(x + sx, y + sy)) return false
    }
    return true
}

/** Text scrolling right to left through the middle of the matrix. */
class Marquee(private val shape: MatrixShape, text: String, private val speed: Float = 14f) {
    private val strip = DotText.layout(text)
    private val loop = strip.width + shape.width + GAP
    private var pos = 0f

    /** Number of times the text has gone all the way through. */
    var loops = 0
        private set

    fun update(dt: Float) {
        pos += dt * speed
        while (pos >= loop) {
            pos -= loop
            loops++
        }
    }

    fun render(c: Canvas, v: Int = 255) {
        c.strip(strip, shape.width - pos.toInt(), DotText.centredTop(shape.height), v)
    }

    private companion object {
        const val GAP = 4
    }
}

/**
 * The player's horizontal position for a sprite sliding along one row (a ship, a paddle):
 * follows the tilt, a finger or the loudness of your voice.
 */
class Slider(private val minX: Int, private val maxX: Int, private val maxSpeed: Float) {
    var x: Float = (minX + maxX) / 2f
        private set
    val cell: Int get() = x.roundToInt().coerceIn(minX, maxX)

    fun centre() {
        x = (minX + maxX) / 2f
    }

    fun update(dt: Float, input: InputFrame, cfg: GameConfig) {
        val span = (maxX - minX).toFloat()
        if (span <= 0f) return
        val target = when (cfg.move) {
            Move.TILT -> {
                val t = (input.gx / cfg.tiltRange).coerceIn(-1f, 1f)
                minX + (t + 1f) / 2f * span
            }
            Move.TOUCH -> input.touchX?.let { minX + it.coerceIn(0f, 1f) * span } ?: x
            Move.MIC -> minX + input.mic.coerceIn(0f, 1f) * span
        }
        val d = target - x
        val step = maxSpeed * dt
        x = if (abs(d) <= step) target else x + step * d.sign
        x = x.coerceIn(minX.toFloat(), maxX.toFloat())
    }
}

/** Valid left edges for [s] on row [top] (all of it on LEDs), or null if it never fits. */
fun slideRange(shape: MatrixShape, s: Sprite, top: Int): IntRange? {
    var lo: Int? = null
    var hi: Int? = null
    for (x in -s.w..shape.width) {
        if (fits(shape, s, x, top)) {
            if (lo == null) lo = x
            hi = x
        }
    }
    val l = lo ?: return null
    val h = hi ?: return null
    return l..h
}

/** Lowest row (scanning up from the bottom) at least [minWidth] LEDs wide. */
fun lowestRowWithWidth(shape: MatrixShape, minWidth: Int): Int {
    for (y in shape.height - 1 downTo 0) if (shape.rowWidth(y) >= minWidth) return y
    return shape.height - 1
}

/** Lives as dim dots on [row], centred. */
fun drawLives(c: Canvas, row: Int, lives: Int, v: Int = 70) {
    if (lives <= 0 || row !in 0 until c.h) return
    val n = lives.coerceAtMost(5)
    val width = n * 2 - 1
    val start = (c.w - width) / 2
    for (i in 0 until n) c.plot(start + i * 2, row, v)
}

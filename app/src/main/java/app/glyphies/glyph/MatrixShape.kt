package app.glyphies.glyph

/**
 * The dots of a Glyph Matrix: a width × height grid where only some cells have an LED.
 * Frames are row-major IntArrays (0..255 per cell); cells without an LED are always 0.
 *
 * Phone (4a) Pro: 13 × 13 grid, 137 LEDs in a disc. Phone (3): 25 × 25 grid, 489 LEDs.
 * Pure Kotlin, so the games run (and are tested) off-device too.
 */
class MatrixShape private constructor(val width: Int, val height: Int, val visible: BooleanArray) {
    val cells: Int get() = width * height
    val ledCount: Int = visible.count { it }

    /** A square device matrix (13 or 25); sprites and other editors use plain rectangles. */
    val isRound: Boolean = ledCount < cells

    /** First and last lit column of each row (empty range when a row has no LED). */
    private val rowRanges: Array<IntRange> = Array(height) { y ->
        var first = -1
        var last = -1
        for (x in 0 until width) {
            if (visible[y * width + x]) {
                if (first < 0) first = x
                last = x
            }
        }
        if (first < 0) IntRange.EMPTY else first..last
    }

    fun index(x: Int, y: Int): Int = y * width + x
    fun inside(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height
    fun isLed(x: Int, y: Int): Boolean = inside(x, y) && visible[y * width + x]
    fun rowRange(y: Int): IntRange = if (y in 0 until height) rowRanges[y] else IntRange.EMPTY
    fun rowWidth(y: Int): Int = rowRange(y).let { if (it.isEmpty()) 0 else it.last - it.first + 1 }

    /** A blank frame for this shape. */
    fun blank(): IntArray = IntArray(cells)

    /** Zeroes the cells that have no LED. */
    fun mask(frame: IntArray): IntArray {
        for (i in frame.indices) if (i < visible.size && !visible[i]) frame[i] = 0
        return frame
    }

    override fun equals(other: Any?): Boolean =
        other is MatrixShape && other.width == width && other.height == height && other.visible.contentEquals(visible)

    override fun hashCode(): Int = (width * 31 + height) * 31 + visible.contentHashCode()

    companion object {
        /** Nothing Phone (4a) Pro: rows of 5, 9, 11, 11, 13 … LEDs, 137 in all. */
        val PRO_4A: MatrixShape = disc(13, intArrayOf(5, 9, 11, 11, 13, 13, 13, 13, 13, 11, 11, 9, 5))

        /** Nothing Phone (3): 489 LEDs on a 25 × 25 grid. */
        val PHONE_3: MatrixShape = disc(
            25,
            intArrayOf(7, 11, 15, 17, 19, 21, 21, 23, 23, 25, 25, 25, 25, 25, 25, 25, 23, 23, 21, 21, 19, 17, 15, 11, 7),
        )

        /** Matrix sizes creations can be made for. */
        val SIZES = intArrayOf(13, 25)

        fun forSize(size: Int): MatrixShape = if (size >= 20) PHONE_3 else PRO_4A

        fun rect(width: Int, height: Int): MatrixShape =
            MatrixShape(width, height, BooleanArray(width * height) { true })

        private fun disc(size: Int, rows: IntArray): MatrixShape {
            require(rows.size == size)
            val visible = BooleanArray(size * size)
            for (y in 0 until size) {
                val start = (size - rows[y]) / 2
                for (x in start until start + rows[y]) visible[y * size + x] = true
            }
            return MatrixShape(size, size, visible)
        }
    }
}

/** Frame helpers shared by the games, the editor and the Glyph output. */
object Frames {
    /** Copies [src] (srcW × srcH) centred into a dstW × dstH frame, cropping what doesn't fit. */
    fun fit(src: IntArray, srcW: Int, srcH: Int, dstW: Int, dstH: Int): IntArray {
        if (srcW == dstW && srcH == dstH) return src.copyOf()
        val out = IntArray(dstW * dstH)
        val ox = (dstW - srcW) / 2
        val oy = (dstH - srcH) / 2
        for (y in 0 until srcH) {
            val ty = y + oy
            if (ty !in 0 until dstH) continue
            for (x in 0 until srcW) {
                val tx = x + ox
                if (tx !in 0 until dstW) continue
                out[ty * dstW + tx] = src[y * srcW + x]
            }
        }
        return out
    }

    /** Full power of an LED in the raw frames Nothing's Glyph service takes (its SDK renders 0..4095). */
    const val RAW_MAX = 4095

    /**
     * Our 0..255 brightness on the service's full 0..[RAW_MAX] scale, at [percent] of full power.
     * A lit dot never rounds down to off.
     */
    fun toRaw(frame: IntArray, percent: Int): IntArray = IntArray(frame.size) { i ->
        val v = frame[i]
        if (v <= 0) 0 else (v.coerceAtMost(255).toLong() * RAW_MAX * percent.coerceIn(1, 100) / (255L * 100)).toInt().coerceIn(1, RAW_MAX)
    }

    /** Lowercase hex, two characters per cell. */
    fun encode(frame: IntArray): String {
        val sb = StringBuilder(frame.size * 2)
        for (v in frame) {
            val b = v.coerceIn(0, 255)
            sb.append(HEX[b shr 4]).append(HEX[b and 15])
        }
        return sb.toString()
    }

    /** Inverse of [encode]; missing or broken data reads as dark cells. */
    fun decode(text: String, cells: Int): IntArray {
        val out = IntArray(cells)
        val n = minOf(cells, text.length / 2)
        for (i in 0 until n) {
            val hi = Character.digit(text[i * 2], 16)
            val lo = Character.digit(text[i * 2 + 1], 16)
            if (hi >= 0 && lo >= 0) out[i] = hi * 16 + lo
        }
        return out
    }

    private val HEX = "0123456789abcdef".toCharArray()
}

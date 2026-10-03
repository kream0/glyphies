package app.glyphies.glyph

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.max

/**
 * Any emoji on the Glyph Matrix, drawn with the phone's own emoji font and reduced to dots:
 * where the emoji is, the LEDs light up by how light it is, and its dark details (eyes,
 * mouths) stay as holes. Used for emoji outside the hand-drawn set.
 */
object EmojiRaster {
    private val cache = HashMap<String, IntArray?>()

    /** The dots of [text] on [shape], or null when it draws nothing (cached; any thread). */
    @Synchronized
    fun render(text: String, shape: MatrixShape): IntArray? {
        val key = "${shape.width}:$text"
        if (cache.containsKey(key)) return cache[key]?.copyOf()
        val frame = runCatching { draw(text, shape) }.getOrNull()
        cache[key] = frame
        return frame?.copyOf()
    }

    private fun draw(text: String, shape: MatrixShape): IntArray? {
        val n = shape.width
        val ss = 8 // pixels per LED when drawing
        val res = n * ss
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = res.toFloat() }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) return null
        // Fit inside the round matrix.
        paint.textSize = res * (res * 0.84f / max(bounds.width(), bounds.height()))
        paint.getTextBounds(text, 0, text.length, bounds)
        val bitmap = Bitmap.createBitmap(res, res, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, (res - bounds.width()) / 2f - bounds.left, (res - bounds.height()) / 2f - bounds.top, paint)
        val px = IntArray(res * res)
        bitmap.getPixels(px, 0, res, 0, 0, res, res)
        bitmap.recycle()

        val cover = FloatArray(n * n)
        val light = FloatArray(n * n)
        for (cy in 0 until n) for (cx in 0 until n) {
            var a = 0f
            var l = 0f
            for (y in cy * ss until (cy + 1) * ss) for (x in cx * ss until (cx + 1) * ss) {
                val c = px[y * res + x]
                val alpha = Color.alpha(c) / 255f
                a += alpha
                l += alpha * (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f
            }
            val i = cy * n + cx
            cover[i] = a / (ss * ss)
            light[i] = if (a > 0f) l / a else 0f
        }
        val lit = (0 until n * n).filter { cover[it] > 0.35f && shape.visible[it] }
        if (lit.isEmpty()) return null
        val lo = lit.minOf { light[it] }
        val hi = lit.maxOf { light[it] }
        val out = IntArray(n * n)
        for (i in lit) {
            out[i] = if (hi - lo < 0.2f) {
                255 // one colour (a red heart…): all lit
            } else {
                val k = (light[i] - lo) / (hi - lo)
                if (k < 0.22f) 0 else (255 * (0.3f + 0.7f * k)).toInt()
            }
        }
        return out
    }
}

package app.glyphies.glyph

import java.text.Normalizer

/** A horizontal run of dot columns (bit n = row n of [DotFont]), ready to draw or scroll. */
class DotStrip(val columns: IntArray) {
    val width: Int get() = columns.size
}

/** Lays text out with [DotFont]; characters it can't draw (emoji, CJK…) are skipped. */
object DotText {
    private const val SPACE_WIDTH = 3
    private val SEPARATOR = intArrayOf(1 shl 4) // a single mid-height dot

    private val replacements = mapOf(
        '‘' to "'", '’' to "'", '“' to "\"", '”' to "\"",
        '–' to "-", '—' to "-", '…' to "...", '×' to "x",
        'ß' to "ss", 'Æ' to "AE", 'æ' to "ae", 'Œ' to "OE", 'œ' to "oe",
        'Ø' to "O", 'ø' to "o", 'Ł' to "L", 'ł' to "l",
    )

    fun layout(text: String): DotStrip {
        val cols = ArrayList<Int>(text.length * 6)
        for (ch in simplify(text)) {
            when {
                ch == ' ' -> repeat(SPACE_WIDTH) { cols += 0 }
                ch == '·' -> {
                    cols.addAll(SEPARATOR.toList())
                    cols += 0
                }
                else -> DotFont.glyph(ch)?.let {
                    cols.addAll(it.toList())
                    cols += 0
                }
            }
        }
        while (cols.isNotEmpty() && cols.last() == 0) cols.removeAt(cols.lastIndex)
        return DotStrip(cols.toIntArray())
    }

    /** Whether every character of [text] can be drawn (spaces and accents count as drawable). */
    fun canDraw(ch: Char): Boolean = ch == ' ' || ch == '·' || DotFont.glyph(simplify(ch.toString()).firstOrNull() ?: ' ') != null

    /** Strips accents (é → e) and maps typographic punctuation to what the dot font has. */
    private fun simplify(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val r = replacements[ch]
            if (r != null) sb.append(r) else sb.append(ch)
        }
        return Normalizer.normalize(sb, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    }

    /** Top row for text whose capitals (rows 1..7 of the font) sit centred on a [height]-row matrix. */
    fun centredTop(height: Int, scale: Int = 1): Int = (height - 7 * scale) / 2 - scale
}

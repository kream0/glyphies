package app.glyphies.ui.components

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.glyphies.engine.InputFrame
import app.glyphies.engine.Marks
import app.glyphies.engine.NoFx
import app.glyphies.engine.Playable
import app.glyphies.glyph.MatrixShape
import app.glyphies.ui.theme.P
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The Glyph Matrix on screen: one rounded square per LED, lit by brightness. With [onPaint] it
 * becomes a canvas: every cell a finger passes over is reported (strokes are continuous).
 * [ghost] is drawn faintly underneath (onion skin); [marks] shows maze markers.
 */
@Composable
fun MatrixView(
    frame: IntArray,
    shape: MatrixShape,
    modifier: Modifier = Modifier,
    ghost: IntArray? = null,
    marks: Boolean = false,
    lit: Color = P.ledOn,
    off: Color = P.ledOff,
    accent: Color = P.accent,
    gap: Float = 0.2f,
    onPaint: ((x: Int, y: Int, first: Boolean) -> Unit)? = null,
    onStrokeEnd: (() -> Unit)? = null,
) {
    val paint by rememberUpdatedState(onPaint)
    val end by rememberUpdatedState(onStrokeEnd)
    val input = if (onPaint != null) {
        Modifier.pointerInput(shape) {
            fun cellAt(o: Offset): Pair<Int, Int> {
                val cell = min(size.width.toFloat() / shape.width, size.height.toFloat() / shape.height)
                val ox = (size.width - cell * shape.width) / 2f
                val oy = (size.height - cell * shape.height) / 2f
                val x = ((o.x - ox) / cell).toInt().coerceIn(0, shape.width - 1)
                val y = ((o.y - oy) / cell).toInt().coerceIn(0, shape.height - 1)
                return x to y
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                var (lx, ly) = cellAt(down.position)
                paint?.invoke(lx, ly, true)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        change.consume()
                        break
                    }
                    change.consume()
                    val (cx, cy) = cellAt(change.position)
                    if (cx != lx || cy != ly) {
                        // Every cell on the way, so fast strokes leave no gaps.
                        val steps = max(abs(cx - lx), abs(cy - ly))
                        for (k in 1..steps) {
                            val x = lx + (cx - lx) * k / steps
                            val y = ly + (cy - ly) * k / steps
                            paint?.invoke(x, y, false)
                        }
                        lx = cx
                        ly = cy
                    }
                }
                end?.invoke()
            }
        }
    } else {
        Modifier
    }

    Canvas(modifier.aspectRatio(shape.width.toFloat() / shape.height).then(input)) {
        val cell = min(size.width / shape.width, size.height / shape.height)
        val ox = (size.width - cell * shape.width) / 2f
        val oy = (size.height - cell * shape.height) / 2f
        val side = cell * (1f - gap)
        val inset = (cell - side) / 2f
        val corner = CornerRadius(side * 0.22f, side * 0.22f)
        for (y in 0 until shape.height) for (x in 0 until shape.width) {
            val i = y * shape.width + x
            if (!shape.visible[i]) continue
            val v = frame.getOrElse(i) { 0 }
            val tl = Offset(ox + x * cell + inset, oy + y * cell + inset)
            val sz = Size(side, side)
            val g = ghost?.getOrElse(i) { 0 } ?: 0
            when {
                marks && Marks.isMark(v) -> {
                    drawRoundRect(off, tl, sz, corner)
                    val c = Offset(tl.x + side / 2, tl.y + side / 2)
                    when (v) {
                        Marks.START -> drawCircle(lit, side * 0.34f, c, style = Stroke(width = side * 0.14f))
                        Marks.GOAL -> drawCircle(accent, side * 0.38f, c)
                        Marks.TRAP -> {
                            val d = side * 0.3f
                            drawLine(accent, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), strokeWidth = side * 0.14f)
                            drawLine(accent, Offset(c.x - d, c.y + d), Offset(c.x + d, c.y - d), strokeWidth = side * 0.14f)
                        }
                    }
                }
                v >= Marks.MIN_LIGHT || (!marks && v > 0) -> drawRoundRect(blend(off, lit, 0.25f + 0.75f * v / 255f), tl, sz, corner)
                g > 0 && !Marks.isMark(g) -> drawRoundRect(blend(off, lit, 0.16f), tl, sz, corner)
                else -> drawRoundRect(off, tl, sz, corner)
            }
        }
    }
}

private fun blend(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * k,
        green = a.green + (b.green - a.green) * k,
        blue = a.blue + (b.blue - a.blue) * k,
        alpha = 1f,
    )
}

/**
 * A playable running on its own with made-up input (for the cards and editor previews): the
 * phone "tilts" slowly back and forth, taps now and then.
 */
@Composable
fun LivePreview(
    key: Any?,
    modifier: Modifier = Modifier,
    fps: Int = 20,
    input: (t: Float, dt: Float) -> InputFrame = { t, dt -> demoInput(t, dt) },
    factory: () -> Playable?,
) {
    var frame by remember(key) { mutableStateOf<IntArray?>(null) }
    var shape by remember(key) { mutableStateOf<MatrixShape?>(null) }
    LaunchedEffect(key) {
        val p = factory() ?: return@LaunchedEffect
        shape = p.shape
        var t = 0f
        var last = SystemClock.elapsedRealtime()
        while (true) {
            val now = SystemClock.elapsedRealtime()
            val dt = ((now - last) / 1000f).coerceIn(0f, 0.1f)
            last = now
            t += dt
            p.update(dt, input(t, dt), NoFx)
            val out = p.shape.blank()
            p.render(out)
            frame = out
            delay(1000L / fps)
        }
    }
    val s = shape
    val f = frame
    if (s != null && f != null) MatrixView(f, s, modifier) else MatrixView(IntArray(169), MatrixShape.PRO_4A, modifier)
}

/**
 * Made-up input for previews: a slow sway, a voice that comes and goes, and now and then a
 * tap (every 6 s) and a shake (2 s after it), so games restart and sand drawings crumble and
 * rebuild on their own.
 */
fun demoInput(t: Float, dt: Float): InputFrame {
    fun every(period: Float, phase: Float): Boolean {
        val now = kotlin.math.floor((t - phase) / period)
        val before = kotlin.math.floor((t - dt - phase) / period)
        return t >= phase && now != before
    }
    val sway = kotlin.math.sin(t * 0.8f)
    return InputFrame(
        gx = sway * 0.8f,
        gy = 0.75f + 0.25f * kotlin.math.cos(t * 0.5f),
        mic = (0.5f + 0.5f * kotlin.math.sin(t * 2.3f)).coerceIn(0f, 1f),
        claps = if (every(1.5f, 0.4f)) 1 else 0,
        presses = if (every(6f, 0.5f)) 1 else 0,
        shakes = if (every(6f, 2.5f)) 1 else 0,
        touchX = 0.5f + 0.45f * sway,
        heading = (t * 30f) % 360f,
        light = 0.5f + 0.5f * kotlin.math.sin(t * 0.7f),
        near = (t.toInt() / 2) % 2 == 0,
    )
}

/** Small dot pictograms, Nothing-style. 'X' = lit dot. */
object DotGlyphs {
    val PEN = listOf(".....", ".....", "..X..", ".....", ".....")
    val ERASE = listOf(".XXX.", "X...X", "X...X", "X...X", ".XXX.")
    val FILL = listOf("XXXXX", "XXXXX", "XXXXX", "XXXXX", "XXXXX")
    val MOVE = listOf("..X..", ".XXX.", "XX.XX", ".XXX.", "..X..")
    val MIRROR = listOf("X.X.X", "X.X.X", "X.X.X", "X.X.X", "X.X.X")
    val ONION = listOf("XX...", "XX...", "..XX.", "..XX.", ".....")
    val START = listOf(".XXX.", "X...X", "X.X.X", "X...X", ".XXX.")
    val GOAL = listOf(".XXX.", "XXXXX", "XXXXX", "XXXXX", ".XXX.")
    val TRAP = listOf("X...X", ".X.X.", "..X..", ".X.X.", "X...X")
}

@Composable
fun DotIcon(pattern: List<String>, color: Color, modifier: Modifier = Modifier.size(20.dp)) {
    Canvas(modifier) {
        val rows = pattern.size
        val cols = pattern.maxOf { it.length }
        val cell = min(size.width / cols, size.height / rows)
        val r = cell * 0.4f
        val ox = (size.width - cell * cols) / 2f
        val oy = (size.height - cell * rows) / 2f
        pattern.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                if (c == 'X') drawCircle(color, r, Offset(ox + x * cell + cell / 2, oy + y * cell + cell / 2))
            }
        }
    }
}

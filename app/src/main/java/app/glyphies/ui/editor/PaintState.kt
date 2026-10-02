package app.glyphies.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.glyphies.engine.Marks
import app.glyphies.glyph.MatrixShape

enum class Tool { PEN, ERASE, FILL, MOVE, START, GOAL, TRAP }

/** Brightness levels offered by the pen (full, bright, dim, faint). */
val LEVELS = intArrayOf(255, 170, 90, 50)

/**
 * Everything the pixel editor edits: the frames of a creation (or a sprite), the current tool,
 * brightness, mirror and onion-skin switches, and undo / redo. [version] goes up on every
 * change, so the screen can save and refresh the Glyph Matrix.
 */
class PaintState(shape: MatrixShape, frames: List<IntArray>) {
    var shape by mutableStateOf(shape)
        private set
    val frames = mutableStateListOf<IntArray>().apply {
        addAll(frames.map { f -> IntArray(shape.cells) { i -> f.getOrElse(i) { 0 } } }.ifEmpty { listOf(shape.blank()) })
    }
    var index by mutableIntStateOf(0)
        private set
    var tool by mutableStateOf(Tool.PEN)
    var level by mutableIntStateOf(255)
    var mirror by mutableStateOf(false)
    var onion by mutableStateOf(false)
    var version by mutableIntStateOf(0)
        private set
    var undoDepth by mutableIntStateOf(0)
        private set
    var redoDepth by mutableIntStateOf(0)
        private set

    private class Snap(val shape: MatrixShape, val frames: List<IntArray>, val index: Int)

    private val undo = ArrayDeque<Snap>()
    private val redo = ArrayDeque<Snap>()

    val current: IntArray get() = frames[index.coerceIn(0, frames.lastIndex)]
    val previous: IntArray? get() = if (index > 0) frames[index - 1] else null

    private var strokeValue = 0
    private var moveFrom: Pair<Int, Int>? = null
    private var moveBase: IntArray? = null

    // ------------------------------------------------------------------ painting

    fun paint(x: Int, y: Int, first: Boolean) {
        if (first) checkpoint()
        val i = y * shape.width + x
        when (tool) {
            Tool.PEN -> {
                if (!shape.isLed(x, y)) return
                // Starting on a dot of the same brightness rubs out instead.
                if (first) strokeValue = if (current[i] == level) 0 else level
                set(x, y, strokeValue)
            }
            Tool.ERASE -> if (shape.isLed(x, y)) set(x, y, 0)
            Tool.FILL -> if (first && shape.isLed(x, y)) fill(x, y, if (current[i] == level) 0 else level)
            Tool.MOVE -> {
                if (first) {
                    moveFrom = x to y
                    moveBase = current.copyOf()
                } else {
                    val (fx, fy) = moveFrom ?: return
                    val base = moveBase ?: return
                    replaceCurrent(shifted(base, x - fx, y - fy))
                }
            }
            Tool.START, Tool.GOAL -> if (first && shape.isLed(x, y)) {
                val mark = if (tool == Tool.START) Marks.START else Marks.GOAL
                val f = current.copyOf()
                for (k in f.indices) if (f[k] == mark) f[k] = 0
                f[i] = mark
                replaceCurrent(f)
            }
            Tool.TRAP -> {
                if (!shape.isLed(x, y)) return
                if (first) strokeValue = if (current[i] == Marks.TRAP) 0 else Marks.TRAP
                set(x, y, strokeValue)
            }
        }
    }

    fun endStroke() {
        moveFrom = null
        moveBase = null
    }

    private fun set(x: Int, y: Int, v: Int) {
        val f = current.copyOf()
        f[y * shape.width + x] = v
        if (mirror) {
            val mx = shape.width - 1 - x
            if (shape.isLed(mx, y)) f[y * shape.width + mx] = v
        }
        if (!f.contentEquals(current)) replaceCurrent(f)
    }

    private fun fill(x: Int, y: Int, v: Int) {
        val f = current.copyOf()
        val target = f[y * shape.width + x]
        if (target == v) return
        val stack = ArrayDeque<Int>()
        stack.add(y * shape.width + x)
        while (stack.isNotEmpty()) {
            val c = stack.removeLast()
            if (f[c] != target || !shape.visible[c]) continue
            f[c] = v
            val cx = c % shape.width
            val cy = c / shape.width
            if (cx > 0) stack.add(c - 1)
            if (cx < shape.width - 1) stack.add(c + 1)
            if (cy > 0) stack.add(c - shape.width)
            if (cy < shape.height - 1) stack.add(c + shape.width)
        }
        if (mirror) {
            // Mirror the filled area too.
            for (yy in 0 until shape.height) for (xx in 0 until shape.width) {
                val a = yy * shape.width + xx
                val b = yy * shape.width + (shape.width - 1 - xx)
                if (f[a] == v && current[a] != v && shape.visible[b]) f[b] = v
            }
        }
        replaceCurrent(f)
    }

    private fun shifted(src: IntArray, dx: Int, dy: Int): IntArray {
        val out = IntArray(src.size)
        for (y in 0 until shape.height) for (x in 0 until shape.width) {
            val v = src[y * shape.width + x]
            if (v == 0) continue
            val nx = x + dx
            val ny = y + dy
            if (shape.isLed(nx, ny)) out[ny * shape.width + nx] = v
        }
        return out
    }

    private fun replaceCurrent(f: IntArray) {
        frames[index.coerceIn(0, frames.lastIndex)] = shape.mask(f)
        version++
    }

    // ------------------------------------------------------------------ frames

    fun select(i: Int) {
        index = i.coerceIn(0, frames.lastIndex)
    }

    fun addFrame(duplicate: Boolean = true) {
        checkpoint()
        frames.add(index + 1, if (duplicate) current.copyOf() else shape.blank())
        index++
        version++
    }

    fun deleteFrame() {
        if (frames.size <= 1) {
            clearFrame()
            return
        }
        checkpoint()
        frames.removeAt(index)
        index = index.coerceAtMost(frames.lastIndex)
        version++
    }

    fun moveFrame(by: Int) {
        val to = index + by
        if (to !in frames.indices) return
        checkpoint()
        val f = frames.removeAt(index)
        frames.add(to, f)
        index = to
        version++
    }

    fun clearFrame() {
        checkpoint()
        replaceCurrent(shape.blank())
    }

    fun flip() {
        checkpoint()
        val f = IntArray(shape.cells) { i -> current[(i / shape.width) * shape.width + (shape.width - 1 - i % shape.width)] }
        replaceCurrent(f)
    }

    fun replaceAll(list: List<IntArray>) {
        checkpoint()
        frames.clear()
        frames.addAll(list.map { shape.mask(it.copyOf()) }.ifEmpty { listOf(shape.blank()) })
        index = 0
        version++
    }

    /** New size for a sprite: keeps what fits, from the top-left corner. */
    fun resize(w: Int, h: Int) {
        if (w == shape.width && h == shape.height) return
        checkpoint()
        val old = shape
        val next = MatrixShape.rect(w, h)
        val copied = frames.map { f ->
            IntArray(w * h) { i ->
                val x = i % w
                val y = i / w
                if (x < old.width && y < old.height) f[y * old.width + x] else 0
            }
        }
        shape = next
        frames.clear()
        frames.addAll(copied)
        version++
    }

    // ------------------------------------------------------------------ undo

    private fun checkpoint() {
        undo.addLast(Snap(shape, frames.map { it.copyOf() }, index))
        if (undo.size > 40) undo.removeFirst()
        redo.clear()
        undoDepth = undo.size
        redoDepth = 0
    }

    fun undo() {
        val s = undo.removeLastOrNull() ?: return
        redo.addLast(Snap(shape, frames.map { it.copyOf() }, index))
        restore(s)
    }

    fun redo() {
        val s = redo.removeLastOrNull() ?: return
        undo.addLast(Snap(shape, frames.map { it.copyOf() }, index))
        restore(s)
    }

    private fun restore(s: Snap) {
        shape = s.shape
        frames.clear()
        frames.addAll(s.frames)
        index = s.index.coerceIn(0, frames.lastIndex)
        undoDepth = undo.size
        redoDepth = redo.size
        version++
    }
}

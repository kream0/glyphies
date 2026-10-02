package app.glyphies.engine

import app.glyphies.glyph.MatrixShape
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Special cell values in level frames drawn in the editor. Real brightness never goes below
 * [Marks.MIN_LIGHT], so these can't be confused with a dim dot.
 */
object Marks {
    const val START = 1
    const val GOAL = 2
    const val TRAP = 3
    const val MIN_LIGHT = 10

    fun isMark(v: Int): Boolean = v in 1 until MIN_LIGHT

    /** The frame as it should light up: markers removed. */
    fun strip(frame: IntArray): IntArray = IntArray(frame.size) { if (isMark(frame[it])) 0 else frame[it] }
}

/**
 * Tilt maze: roll the ball to the blinking goal. Each level is a frame of the creation:
 * lit dots are walls, plus a start, a goal and traps (they send you back to the start).
 */
class MazeGame(
    override val shape: MatrixShape,
    levels: List<IntArray>,
    private val cfg: GameConfig = GameConfig(),
    override val id: String = "maze",
) : Playable {
    override val needs: Set<Need> = setOf(Need.TILT)

    private class Level(val walls: BooleanArray, val traps: BooleanArray, val start: Int, val goal: Int, val look: IntArray)

    private val w = shape.width
    private val h = shape.height
    private val levels: List<Level> = levels.map { build(it) }.ifEmpty { listOf(build(shape.blank())) }

    private var index = 0
    private var bx = 0f
    private var by = 0f
    private var vx = 0f
    private var vy = 0f
    private var t = 0f
    private var levelT = 0f
    private var score = 0
    private var intro = 0.8f
    private var marquee: Marquee? = null
    private var doneT = 0f

    init {
        place()
    }

    private val level: Level get() = levels[index]

    private fun build(frame: IntArray): Level {
        val walls = BooleanArray(shape.cells) { i -> !shape.visible[i] || frame.getOrElse(i) { 0 } >= Marks.MIN_LIGHT }
        val traps = BooleanArray(shape.cells) { i -> frame.getOrElse(i) { 0 } == Marks.TRAP }
        val free = (0 until shape.cells).filter { !walls[it] && !traps[it] }
        var start = frame.indexOfFirst { it == Marks.START }.takeIf { it >= 0 && !walls[it] }
            ?: free.minByOrNull { (it / w) * 100 + abs(it % w - w / 2) } ?: (shape.cells / 2)
        var goal = frame.indexOfFirst { it == Marks.GOAL }.takeIf { it >= 0 && !walls[it] } ?: farthest(walls, start)
        if (goal == start) goal = farthest(walls, start)
        if (walls[start]) start = free.firstOrNull() ?: start
        val look = IntArray(shape.cells) { i -> if (walls[i] && shape.visible[i]) minOf(frame[i], WALL) else 0 }
        return Level(walls, traps, start, goal, look)
    }

    /** The free cell farthest (in steps) from [from]. */
    private fun farthest(walls: BooleanArray, from: Int): Int {
        val dist = IntArray(shape.cells) { -1 }
        val queue = ArrayDeque<Int>()
        dist[from] = 0
        queue.add(from)
        var last = from
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            last = c
            val x = c % w
            val y = c / w
            for ((dx, dy) in NEIGHBOURS) {
                val nx = x + dx
                val ny = y + dy
                if (!shape.inside(nx, ny)) continue
                val n = ny * w + nx
                if (walls[n] || dist[n] >= 0) continue
                dist[n] = dist[c] + 1
                queue.add(n)
            }
        }
        return last
    }

    private fun place() {
        bx = level.start % w + 0.5f
        by = level.start / w + 0.5f
        vx = 0f
        vy = 0f
    }

    override fun restart() {
        index = 0
        score = 0
        levelT = 0f
        intro = 0.8f
        marquee = null
        place()
    }

    private fun blocked(x: Float, y: Float): Boolean {
        val cx = x.toInt()
        val cy = y.toInt()
        if (x < 0f || y < 0f || cx >= w || cy >= h) return true
        return level.walls[cy * w + cx]
    }

    override fun update(dt: Float, input: InputFrame, fx: Fx) {
        t += dt
        marquee?.let {
            it.update(dt)
            doneT += dt
            if (doneT > 1.2f && input.presses > 0) restart()
            return
        }
        if (intro > 0f) {
            intro -= dt
            return
        }
        levelT += dt
        val accel = h * 2.4f * cfg.pace
        vx += input.gx * accel * dt
        vy += input.gy * accel * dt
        val damping = 1f - (1.6f * dt).coerceAtMost(0.5f)
        vx *= damping
        vy *= damping
        val maxV = h * 1.4f
        val v = sqrt(vx * vx + vy * vy)
        if (v > maxV) {
            vx *= maxV / v
            vy *= maxV / v
        }
        // Small steps so the ball never jumps over a wall.
        val n = ceil(max(abs(vx), abs(vy)) * dt / 0.3f).toInt().coerceAtLeast(1)
        repeat(n) {
            val sx = vx * dt / n
            val sy = vy * dt / n
            if (blocked(bx + sx, by)) {
                vx = -vx * 0.3f
            } else {
                bx += sx
            }
            if (blocked(bx, by + sy)) {
                vy = -vy * 0.3f
            } else {
                by += sy
            }
        }
        val cell = by.toInt() * w + bx.toInt()
        if (level.traps[cell]) {
            fx.buzz(Buzz.HIT)
            place()
            return
        }
        if (cell == level.goal) {
            score += max(10, 100 - (levelT * 2).roundToInt())
            levelT = 0f
            if (index + 1 < levels.size) {
                fx.buzz(Buzz.WIN)
                index++
                intro = 0.8f
                place()
            } else {
                fx.buzz(Buzz.WIN)
                marquee = Marquee(shape, "${cfg.labels.win}  ·  $score")
                doneT = 0f
                fx.gameOver(score)
            }
        }
    }

    override fun render(out: IntArray) {
        val c = Canvas(shape, out)
        marquee?.let {
            it.render(c)
            return
        }
        if (intro > 0f && levels.size > 1) {
            c.centred((index + 1).toString())
            return
        }
        val l = level
        for (i in l.look.indices) if (l.look[i] > 0) out[i] = l.look[i]
        for (i in l.traps.indices) if (l.traps[i]) out[i] = if ((t * 2).toInt() % 2 == 0) 40 else 75
        out[l.goal] = if ((t * 4).toInt() % 2 == 0) 255 else 60
        c.plot(bx.toInt(), by.toInt(), 255)
    }

    override fun hud(): Hud = Hud(
        score = score,
        level = index + 1,
        detail = "%.1f s".format(levelT),
        over = marquee != null,
    )

    private companion object {
        const val WALL = 150
        val NEIGHBOURS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    }
}

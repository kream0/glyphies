package app.glyphies.engine

import app.glyphies.glyph.MatrixShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Falling sand on the Glyph Matrix, after Adafruit's PixelDust: every grain has a sub-pixel
 * position and a velocity; gravity (from the accelerometer) pushes them, and a grain that would
 * land on an occupied dot skids along the free axis or bounces. One grain per LED; cells
 * without an LED and [walls] are solid, so the round edge of the matrix holds the sand.
 */
class SandSim(
    val shape: MatrixShape,
    walls: BooleanArray? = null,
    private val random: Random = Random.Default,
) {
    private val w = shape.width
    private val h = shape.height

    /** -1 free, -2 solid, otherwise the index of the grain sitting there. */
    private val occ = IntArray(w * h) { i ->
        if (!shape.visible[i] || (walls != null && walls[i])) SOLID else FREE
    }

    /** How many grains fit. */
    val capacity: Int = occ.count { it == FREE }

    private val px = FloatArray(capacity)
    private val py = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val lum = IntArray(capacity)
    private val order = IntArray(capacity)
    private val keys = FloatArray(capacity)

    var count: Int = 0
        private set

    /** Bounce: share of the speed a grain keeps when it hits something (0..1). */
    var elasticity: Float = 0.45f

    /** Speed gained per step under 1 g, in dots per step². */
    var accel: Float = 0.2f

    fun isSolid(x: Int, y: Int): Boolean = x !in 0 until w || y !in 0 until h || occ[y * w + x] == SOLID
    fun isFree(x: Int, y: Int): Boolean = x in 0 until w && y in 0 until h && occ[y * w + x] == FREE

    /** Puts a grain at rest on (x, y) if it's free. */
    fun add(x: Int, y: Int, brightness: Int = 255): Boolean {
        if (count >= capacity || !isFree(x, y)) return false
        val i = count++
        px[i] = x + 0.5f
        py[i] = y + 0.5f
        vx[i] = 0f
        vy[i] = 0f
        lum[i] = brightness.coerceIn(1, 255)
        occ[y * w + x] = i
        return true
    }

    /** Takes the most recently added grain away. */
    fun removeLast(): Boolean {
        if (count == 0) return false
        val i = --count
        val cell = cellOf(px[i], py[i])
        if (occ[cell] == i) occ[cell] = FREE
        return true
    }

    /** Moves the grain on (fromX, fromY) to the free cell (toX, toY), keeping it moving that way. */
    fun transfer(fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
        if (fromX !in 0 until w || fromY !in 0 until h || !isFree(toX, toY)) return false
        val i = occ[fromY * w + fromX]
        if (i < 0) return false
        occ[fromY * w + fromX] = FREE
        occ[toY * w + toX] = i
        px[i] = toX + 0.5f
        py[i] = toY + 0.5f
        vx[i] = 0f
        vy[i] = (toY - fromY).coerceIn(-1, 1) * 0.5f
        return true
    }

    fun hasGrain(x: Int, y: Int): Boolean = x in 0 until w && y in 0 until h && occ[y * w + x] >= 0

    fun clear() {
        while (count > 0) removeLast()
    }

    /** Scatters [n] grains on random free cells; [textured] varies their brightness like real sand. */
    fun sprinkle(n: Int, textured: Boolean = true) {
        val free = (0 until w * h).filter { occ[it] == FREE }.shuffled(random)
        for (cell in free.take(n.coerceAtMost(capacity - count))) {
            add(cell % w, cell / w, if (textured) grainLevel() else 255)
        }
    }

    fun grainLevel(): Int = 140 + random.nextInt(116)

    /** A kick in random directions (a shake of the phone). */
    fun shake(strength: Float = 0.8f) {
        for (i in 0 until count) {
            vx[i] += (random.nextFloat() * 2f - 1f) * strength
            vy[i] += (random.nextFloat() * 2f - 1f) * strength
        }
    }

    /**
     * One physics step. [gx], [gy]: gravity along the matrix in g; [gz]: across it. When the
     * phone lies flat the grains barely move, as real sand would.
     */
    fun step(gx: Float, gy: Float, gz: Float = 0f) {
        if (count == 0) return
        // A little randomness makes tall stacks topple; more of it when the phone stands up.
        val jitter = 0.004f + 0.02f * (1f - abs(gz).coerceAtMost(1f))
        val ax = gx * accel
        val ay = gy * accel
        for (i in 0 until count) {
            vx[i] += ax + (random.nextFloat() * 2f - 1f) * jitter
            vy[i] += ay + (random.nextFloat() * 2f - 1f) * jitter
            val v2 = vx[i] * vx[i] + vy[i] * vy[i]
            if (v2 > MAX_V * MAX_V) { // terminal velocity: never more than a dot per step
                val k = MAX_V / sqrt(v2)
                vx[i] *= k
                vy[i] *= k
            }
        }

        // Grains nearest the "floor" move first, so a falling column doesn't leave gaps.
        for (i in 0 until count) {
            order[i] = i
            keys[i] = -(px[i] * gx + py[i] * gy)
        }
        sortOrder()

        // Gravity rounded to one of the 8 neighbours, for the avalanche rule below.
        val strength = sqrt(gx * gx + gy * gy)
        val dir = if (strength < MIN_SLIDE) -1 else ((atan2(gy, gx) / (PI.toFloat() / 4f)).roundToInt() + 8) % 8
        for (k in 0 until count) {
            val i = order[k]
            val before = cellOf(px[i], py[i])
            move(i)
            if (dir >= 0 && cellOf(px[i], py[i]) == before) avalanche(i, dir, gx, gy)
        }
    }

    /**
     * A grain that couldn't move rolls off whatever it sits on: it tries the two cells 45° either
     * side of "down" and takes a free one that's lower. That gives sand its slopes, and is what
     * makes a pile spill over when the phone is tipped.
     */
    private fun avalanche(i: Int, dir: Int, gx: Float, gy: Float) {
        if (random.nextFloat() > SLIDE_CHANCE) return
        val x = px[i].toInt()
        val y = py[i].toInt()
        val first = if (random.nextBoolean()) 1 else 7
        for (turn in intArrayOf(first, 8 - first)) {
            val d = (dir + turn) % 8
            val nx = x + DX[d]
            val ny = y + DY[d]
            if (!isFree(nx, ny)) continue
            // Only downhill (with gravity this sideways, "down" may be up the screen).
            if (DX[d] * gx + DY[d] * gy <= 0.05f) continue
            occ[y * w + x] = FREE
            occ[ny * w + nx] = i
            px[i] = nx + 0.5f
            py[i] = ny + 0.5f
            vx[i] = DX[d] * SLIDE_SPEED
            vy[i] = DY[d] * SLIDE_SPEED
            return
        }
    }

    private fun move(i: Int) {
        val x = px[i]
        val y = py[i]
        var nx = x + vx[i]
        var ny = y + vy[i]
        if (nx < 0f) {
            nx = 0f
            vx[i] = -vx[i] * elasticity
        } else if (nx >= w) {
            nx = w - EPS
            vx[i] = -vx[i] * elasticity
        }
        if (ny < 0f) {
            ny = 0f
            vy[i] = -vy[i] * elasticity
        } else if (ny >= h) {
            ny = h - EPS
            vy[i] = -vy[i] * elasticity
        }
        val ox = x.toInt()
        val oy = y.toInt()
        val cx = nx.toInt()
        val cy = ny.toInt()
        if ((cx != ox || cy != oy) && occ[cy * w + cx] != FREE) {
            when {
                cy == oy -> { // blocked sideways
                    nx = x
                    vx[i] = -vx[i] * elasticity
                }
                cx == ox -> { // blocked up / down
                    ny = y
                    vy[i] = -vy[i] * elasticity
                }
                else -> { // diagonal: skid along one axis if possible, faster axis first
                    val xFirst = abs(vx[i]) >= abs(vy[i])
                    val xFree = occ[oy * w + cx] == FREE
                    val yFree = occ[cy * w + ox] == FREE
                    if (xFirst && xFree || !xFirst && !yFree && xFree) {
                        ny = y
                        vy[i] = -vy[i] * elasticity
                    } else if (yFree) {
                        nx = x
                        vx[i] = -vx[i] * elasticity
                    } else {
                        nx = x
                        ny = y
                        vx[i] = -vx[i] * elasticity
                        vy[i] = -vy[i] * elasticity
                    }
                }
            }
        }
        val from = oy * w + ox
        val to = ny.toInt() * w + nx.toInt()
        if (from != to) {
            if (occ[from] == i) occ[from] = FREE
            occ[to] = i
        }
        px[i] = nx
        py[i] = ny
    }

    /** Insertion sort of [order] by [keys]; grains are mostly in order already from last step. */
    private fun sortOrder() {
        for (a in 1 until count) {
            val idx = order[a]
            val key = keys[idx]
            var b = a - 1
            while (b >= 0 && keys[order[b]] > key) {
                order[b + 1] = order[b]
                b--
            }
            order[b + 1] = idx
        }
    }

    private fun cellOf(x: Float, y: Float): Int = y.toInt().coerceIn(0, h - 1) * w + x.toInt().coerceIn(0, w - 1)

    fun render(out: IntArray, scale: Float = 1f) {
        for (i in 0 until count) {
            val cell = cellOf(px[i], py[i])
            out[cell] = maxOf(out[cell], (lum[i] * scale).toInt().coerceIn(1, 255))
        }
    }

    /** Average speed of the grains, in dots per step (to tell when the sand has settled). */
    fun motion(): Float {
        if (count == 0) return 0f
        var s = 0f
        for (i in 0 until count) s += abs(vx[i]) + abs(vy[i])
        return s / count
    }

    /** Grain positions as cells (for tests and the editor's crumble effect). */
    fun cells(): IntArray = IntArray(count) { cellOf(px[it], py[it]) }

    private companion object {
        const val FREE = -1
        const val SOLID = -2
        const val MAX_V = 0.95f
        const val EPS = 0.001f
        const val MIN_SLIDE = 0.15f
        const val SLIDE_CHANCE = 0.5f
        const val SLIDE_SPEED = 0.25f

        // The 8 neighbours, counter-clockwise from "right" in screen terms (y grows downwards).
        val DX = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
        val DY = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
    }
}

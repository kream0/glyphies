package app.glyphies.engine

import app.glyphies.glyph.MatrixShape
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Breakout: the lit dots of each level frame are bricks; the paddle slides along the bottom
 * and the ball bounces off the round edge. Launch with a tap (or on its own with AUTO).
 */
class BricksGame(
    override val shape: MatrixShape,
    levels: List<IntArray>,
    paddle: Sprite? = null,
    private val cfg: GameConfig = GameConfig(),
    override val id: String = "bricks",
    private val random: Random = Random.Default,
) : Playable {
    override val needs: Set<Need> = cfg.needs

    private val w = shape.width
    private val h = shape.height
    private val paddle: Sprite = (paddle?.trimmed()?.takeIf { it.h == 1 && !it.isEmpty })
        ?: Sprite(if (w >= 20) 5 else 3, 1, IntArray(if (w >= 20) 5 else 3) { 255 })
    private val row = lowestRowWithWidth(shape, max(this.paddle.w + 4, (w * 0.65f).toInt()))
    private val range = slideRange(shape, this.paddle, row) ?: (0..max(0, w - this.paddle.w))
    private val slider = Slider(range.first, range.last, maxSpeed = w * 1.4f)
    private val livesRow = row + 1

    private val levels: List<IntArray> = levels
        .map { f -> IntArray(shape.cells) { i -> if (shape.visible[i] && f.getOrElse(i) { 0 } >= Marks.MIN_LIGHT && i / w < row - 2) f[i] else 0 } }
        .filter { f -> f.any { it > 0 } }
        .ifEmpty { listOf(defaultLevel()) }

    private var index = 0
    private var round = 0
    private lateinit var bricks: IntArray
    private var bx = 0f
    private var by = 0f
    private var vx = 0f
    private var vy = 0f
    private var held = true
    private var holdT = 0f
    private var score = 0
    private var lives = cfg.lives
    private var marquee: Marquee? = null
    private var overT = 0f
    private var flash = 0f

    init {
        load()
    }

    private fun defaultLevel(): IntArray {
        val f = shape.blank()
        val top = if (h >= 20) 4 else 2
        val rows = if (h >= 20) 4 else 3
        for (y in top until top + rows) for (x in 0 until w) {
            if (shape.isLed(x, y)) f[y * w + x] = if ((y - top) % 2 == 0) 255 else 150
        }
        return f
    }

    private fun load() {
        bricks = levels[index].copyOf()
        serve()
    }

    private fun serve() {
        held = true
        holdT = 0f
        vx = 0f
        vy = 0f
    }

    override fun restart() {
        index = 0
        round = 0
        score = 0
        lives = cfg.lives
        marquee = null
        slider.centre()
        load()
    }

    private val ballSpeed: Float get() = h * 0.6f * cfg.pace * (1f + round * 0.15f) * (1f + score / 300f).coerceAtMost(1.4f)

    override fun update(dt: Float, input: InputFrame, fx: Fx) {
        marquee?.let {
            it.update(dt)
            overT += dt
            if (overT > 1.2f && input.presses > 0) restart()
            return
        }
        flash -= dt
        slider.update(dt, input, cfg)
        val pc = slider.cell
        if (held) {
            holdT += dt
            bx = pc + paddle.w / 2 + 0.5f
            by = row - 0.5f
            val go = if (cfg.trigger == Trigger.AUTO) holdT > 1f else cfg.fired(input) && holdT > 0.2f
            if (go) {
                held = false
                val s = ballSpeed
                vx = s * (if (random.nextBoolean()) 0.45f else -0.45f)
                vy = -s * 0.9f
            }
            return
        }
        val n = ceil(max(abs(vx), abs(vy)) * dt / 0.25f).toInt().coerceAtLeast(1)
        repeat(n) { if (!held && marquee == null) stepBall(dt / n, fx) }
    }

    private fun solidOrBrick(x: Float, y: Float): Int {
        // -1: the edge, 0: free, otherwise the brick's index + 1
        if (x < 0f || x >= w || y < 0f) return -1
        val cx = x.toInt()
        val cy = y.toInt()
        if (cy >= h) return 0
        if (cy > row) return 0 // below the paddle is the gutter, not a wall
        if (!shape.visible[cy * w + cx]) return -1
        val i = cy * w + cx
        return if (bricks[i] > 0) i + 1 else 0
    }

    private fun stepBall(dt: Float, fx: Fx) {
        val nx = bx + vx * dt
        val ny = by + vy * dt
        var bounced = false
        val hx = solidOrBrick(nx, by)
        if (hx != 0) {
            vx = -vx
            breakBrick(hx, fx)
            bounced = true
        }
        val hy = solidOrBrick(bx, ny)
        if (hy != 0) {
            vy = -vy
            breakBrick(hy, fx)
            bounced = true
        }
        if (!bounced) {
            val hd = solidOrBrick(nx, ny)
            if (hd != 0) {
                vx = -vx
                vy = -vy
                breakBrick(hd, fx)
                bounced = true
            }
        }
        if (!bounced) {
            bx = nx
            by = ny
        }

        // Paddle
        val pc = slider.cell
        if (vy > 0f && by.toInt() == row - 1 && by + vy * dt >= row) {
            val x = bx.toInt()
            if (x in pc - 1..pc + paddle.w) {
                val centre = pc + paddle.w / 2f
                val off = ((bx - centre) / (paddle.w / 2f + 1f)).coerceIn(-1f, 1f)
                val s = ballSpeed
                vx = s * off * 0.85f
                vy = -sqrt((s * s - vx * vx).coerceAtLeast(s * s * 0.25f))
                fx.buzz(Buzz.TICK)
            }
        }
        // Keep it from crawling sideways forever.
        val s = sqrt(vx * vx + vy * vy)
        if (s > 0f && abs(vy) < s * 0.3f) vy = (if (vy < 0f) -1f else 1f) * s * 0.3f

        if (by >= row + 1) {
            lives--
            fx.buzz(if (lives <= 0) Buzz.BIG else Buzz.HIT)
            flash = 0.5f
            if (lives <= 0) {
                marquee = Marquee(shape, "${cfg.labels.gameOver}  ·  $score")
                overT = 0f
                fx.gameOver(score)
            } else {
                serve()
            }
        }
    }

    private fun breakBrick(hit: Int, fx: Fx) {
        if (hit <= 0) return
        val i = hit - 1
        bricks[i] = 0
        score += 5
        fx.buzz(Buzz.TICK)
        if (bricks.none { it > 0 }) {
            fx.buzz(Buzz.WIN)
            score += 50
            index++
            if (index >= levels.size) {
                index = 0
                round++
            }
            load()
        }
    }

    override fun render(out: IntArray) {
        val c = Canvas(shape, out)
        marquee?.let {
            it.render(c)
            return
        }
        for (i in bricks.indices) if (bricks[i] > 0) out[i] = minOf(bricks[i], 200)
        c.sprite(paddle, slider.cell, row, if (flash > 0f) 0.4f else 1f)
        c.plot(bx.toInt(), by.toInt(), 255)
        drawLives(c, livesRow, lives)
    }

    override fun hud(): Hud = Hud(score = score, lives = lives.coerceAtLeast(0), level = index + 1 + round * levels.size, over = marquee != null)
}

package app.glyphies.engine

import app.glyphies.glyph.MatrixShape
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fly between the pipes (Flappy-style). The bird drops; a tap, a clap, a shake, or simply
 * your voice (MIC: the louder, the higher) keeps it up. The round edge of the matrix is
 * the ground and the sky.
 */
class FlyGame(
    override val shape: MatrixShape,
    bird: Sprite,
    private val cfg: GameConfig = GameConfig(trigger = Trigger.TAP),
    override val id: String = "fly",
    private val random: Random = Random.Default,
) : Playable {
    override val needs: Set<Need> = cfg.needs

    private val bird = bird.trimmed()
    private val w = shape.width
    private val h = shape.height
    private val bx = w / 4
    private val pipeW = if (w >= 20) 2 else 1
    private val gap = if (h >= 20) 8 else 5
    private val spacing = if (w >= 20) 11 else 7

    private var y = h / 2f - this.bird.h / 2f
    private var vy = 0f
    private var t = 0f
    private var started = false

    private class Pipe(var x: Float, val gapTop: Int, var passed: Boolean = false)

    private val pipes = ArrayList<Pipe>()
    private var score = 0
    private var crashT = -1f
    private var marquee: Marquee? = null
    private var overT = 0f

    private val gravity = h * 1.5f
    private val flap = h * 0.5f
    private val speed get() = w * 0.32f * cfg.pace * (1f + score / 40f).coerceAtMost(1.6f)

    override fun restart() {
        y = h / 2f - bird.h / 2f
        vy = 0f
        started = false
        pipes.clear()
        score = 0
        crashT = -1f
        marquee = null
    }

    override fun update(dt: Float, input: InputFrame, fx: Fx) {
        t += dt
        marquee?.let {
            it.update(dt)
            overT += dt
            if (overT > 1.2f && input.presses > 0) restart()
            return
        }
        if (crashT >= 0f) {
            crashT += dt
            if (crashT > 0.9f) {
                marquee = Marquee(shape, "${cfg.labels.gameOver}  ·  $score")
                overT = 0f
                fx.gameOver(score)
            }
            return
        }
        val flapped = cfg.trigger != Trigger.MIC && cfg.fired(input)
        if (!started) {
            // Hover until the first flap (or the first sound, when flying by voice).
            y = h / 2f - bird.h / 2f + sin(t * 4f) * 0.6f
            if (flapped || cfg.trigger == Trigger.MIC && input.mic > 0.3f || cfg.trigger == Trigger.AUTO) {
                started = true
                vy = -flap
            }
            return
        }
        if (cfg.trigger == Trigger.MIC) {
            // Voice lifts: silence falls, a steady "aaah" hovers, shouting climbs.
            vy += (gravity - input.mic * gravity * 2.2f) * dt
            vy = vy.coerceIn(-flap, flap * 1.3f)
        } else {
            if (flapped) {
                vy = -flap
                fx.buzz(Buzz.TICK)
            }
            vy += gravity * dt
        }
        y += vy * dt

        // Pipes
        val s = speed
        for (p in pipes) p.x -= s * dt
        pipes.removeAll { it.x < -pipeW - 1 }
        val last = pipes.lastOrNull()
        if (last == null || last.x <= w - spacing) {
            val margin = 2
            val top = margin + random.nextInt(max(1, h - gap - margin * 2 + 1))
            pipes += Pipe((last?.x ?: w.toFloat()) + if (last == null) 0f else spacing.toFloat(), top)
        }
        for (p in pipes) {
            if (!p.passed && p.x + pipeW <= bx) {
                p.passed = true
                score++
                fx.buzz(Buzz.TICK)
            }
        }

        if (crashed()) {
            crashT = 0f
            fx.buzz(Buzz.BIG)
        }
    }

    private fun crashed(): Boolean {
        val top = y.toInt()
        for (sy in 0 until bird.h) for (sx in 0 until bird.w) {
            if (!bird.lit(sx, sy)) continue
            val cx = bx + sx
            val cy = top + sy
            if (!shape.isLed(cx, cy)) return true
            if (pipeAt(cx, cy)) return true
        }
        return false
    }

    private fun pipeAt(x: Int, y: Int): Boolean = pipes.any { p ->
        val px = p.x.toInt()
        x in px until px + pipeW && (y < p.gapTop || y >= p.gapTop + gap)
    }

    override fun render(out: IntArray) {
        val c = Canvas(shape, out)
        marquee?.let {
            it.render(c)
            return
        }
        for (p in pipes) {
            val px = p.x.toInt()
            for (x in px until px + pipeW) for (yy in 0 until h) {
                if (yy < p.gapTop || yy >= p.gapTop + gap) c.plot(x, yy, 120)
            }
        }
        val blink = crashT >= 0f && (crashT * 10).toInt() % 2 == 0
        if (!blink) c.sprite(bird, bx, y.toInt())
    }

    override fun hud(): Hud = Hud(score = score, over = marquee != null)
}

package app.glyphies.engine

import app.glyphies.glyph.MatrixShape
import kotlin.math.max
import kotlin.random.Random

/**
 * Things fall from the top; the player slides along the bottom.
 * - [catch] = false (Dodge): every falling thing is an obstacle; each one avoided scores.
 * - [catch] = true (Catch): catch the items, avoid the bombs (if there's a bomb sprite);
 *   a missed item costs a life too.
 */
class FallingGame(
    override val shape: MatrixShape,
    private val catch: Boolean,
    player: Sprite,
    item: Sprite,
    bomb: Sprite? = null,
    private val cfg: GameConfig = GameConfig(),
    override val id: String = if (catch) "catch" else "dodge",
    private val random: Random = Random.Default,
) : Playable {
    override val needs: Set<Need> = cfg.needs

    private val player = player.trimmed()
    private val item = item.trimmed()
    private val bomb = bomb?.trimmed()?.takeUnless { it.isEmpty }

    private val w = shape.width
    private val h = shape.height
    private val bottom = lowestRowWithWidth(shape, max(this.player.w + 4, (w * 0.65f).toInt()))
    private val top = bottom - this.player.h + 1
    private val range = slideRange(shape, this.player, top) ?: (0..max(0, w - this.player.w))
    private val slider = Slider(range.first, range.last, maxSpeed = w * 1.2f)
    private val livesRow = bottom + 1

    private class Drop(val sprite: Sprite, val bad: Boolean, val x: Int, var y: Float)

    private val drops = ArrayList<Drop>()
    private var spawnT = 0.5f
    private var elapsed = 0f
    private var score = 0
    private var lives = cfg.lives
    private var hurt = 0f
    private var countdown = 1.5f
    private var marquee: Marquee? = null
    private var overT = 0f

    override fun restart() {
        drops.clear()
        spawnT = 0.5f
        elapsed = 0f
        score = 0
        lives = cfg.lives
        hurt = 0f
        countdown = 1.5f
        marquee = null
        slider.centre()
    }

    private val fallSpeed: Float get() = h * 0.35f * cfg.pace * (1f + elapsed / 90f).coerceAtMost(2.2f)

    override fun update(dt: Float, input: InputFrame, fx: Fx) {
        marquee?.let {
            it.update(dt)
            overT += dt
            if (overT > 1.2f && input.presses > 0) restart()
            return
        }
        slider.update(dt, input, cfg)
        if (countdown > 0f) {
            countdown -= dt
            return
        }
        elapsed += dt
        hurt -= dt

        spawnT -= dt
        if (spawnT <= 0f) {
            spawn()
            spawnT = (1.3f / cfg.pace / (1f + elapsed / 60f)).coerceAtLeast(0.32f) * (0.7f + random.nextFloat() * 0.6f)
        }

        val px = slider.cell
        val speed = fallSpeed
        val it = drops.iterator()
        while (it.hasNext()) {
            val d = it.next()
            d.y += speed * dt
            val dy = d.y.toInt()
            if (overlaps(d.sprite, d.x, dy, player, px, top)) {
                it.remove()
                if (catch && !d.bad) {
                    score++
                    fx.buzz(Buzz.TICK)
                } else {
                    loseLife(fx)
                }
                continue
            }
            if (dy > bottom) {
                it.remove()
                if (catch && !d.bad) loseLife(fx) else if (!catch) score++
            }
        }
        if (lives <= 0 && marquee == null) {
            marquee = Marquee(shape, "${cfg.labels.gameOver}  ·  $score")
            overT = 0f
            fx.gameOver(score)
        }
    }

    private fun loseLife(fx: Fx) {
        if (hurt > 0f && !catch) return
        lives--
        hurt = 0.8f
        fx.buzz(if (lives <= 0) Buzz.BIG else Buzz.HIT)
    }

    private fun spawn() {
        val bad = if (catch) bomb != null && random.nextFloat() < 0.28f else true
        val s = if (catch && bad) bomb!! else item
        // Anywhere the player can reach, so everything can be caught (or dodged).
        val lo = range.first
        val hi = max(lo, range.last + player.w - s.w)
        val x = lo + random.nextInt(hi - lo + 1)
        drops += Drop(s, bad, x, -s.h.toFloat())
    }

    override fun render(out: IntArray) {
        val c = Canvas(shape, out)
        marquee?.let {
            it.render(c)
            return
        }
        if (countdown > 0f) {
            c.centred((countdown / 0.5f).toInt().plus(1).coerceIn(1, 3).toString())
            return
        }
        for (d in drops) c.sprite(d.sprite, d.x, d.y.toInt(), if (d.bad && catch) 0.55f else 1f)
        val blink = hurt > 0f && (hurt * 10).toInt() % 2 == 0
        if (!blink) c.sprite(player, slider.cell, top)
        drawLives(c, livesRow, lives)
    }

    override fun hud(): Hud = Hud(score = score, lives = lives.coerceAtLeast(0), over = marquee != null)
}

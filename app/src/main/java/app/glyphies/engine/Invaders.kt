package app.glyphies.engine

import app.glyphies.glyph.MatrixShape
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/** The pictures of a shooter: built-in ones per matrix size, or drawn in the editor. */
data class ShooterSprites(
    val ship: Sprite,
    val invader: Sprite,
    /** Second animation frame of the invaders (they "walk"); null to keep them still. */
    val invaderAlt: Sprite? = null,
    /** Every fourth wave is a single big invader; null for no boss waves. */
    val boss: Sprite? = null,
    val bossAlt: Sprite? = null,
    val ufo: Sprite? = null,
) {
    companion object {
        fun defaultFor(shape: MatrixShape): ShooterSprites = if (shape.width >= 20) {
            ShooterSprites(
                ship = Sprite.parse("..#..", ".###.", "#####"),
                invader = Sprite.parse(".###.", "#.#.#", "#####", ".#.#."),
                invaderAlt = Sprite.parse(".###.", "#.#.#", "#####", "#...#"),
                boss = Sprite.parse("..###..", ".#####.", "##.#.##", "#######", ".#.#.#.", "#.....#"),
                bossAlt = Sprite.parse("..###..", ".#####.", "##.#.##", "#######", ".#.#.#.", ".#...#."),
                ufo = Sprite.parse(".###.", "#+#+#"),
            )
        } else {
            ShooterSprites(
                ship = Sprite.parse(".#.", "###"),
                invader = Sprite.parse("###", "#.#"),
                invaderAlt = Sprite.parse("###", ".#."),
                boss = Sprite.parse(".###.", "##.##", "#.#.#"),
                bossAlt = Sprite.parse(".###.", "##.##", ".#.#."),
                ufo = Sprite.parse("##"),
            )
        }
    }
}

/**
 * Space Invaders for the Glyph Matrix. Tilt the phone to slide the ship; it fires on its own
 * (or on a tap / volume key, a clap or a shake). A small formation marches, bounces off the
 * round edge and comes down; every fourth wave is a boss. Lives are the dim dots at the bottom.
 */
class InvadersGame(
    override val shape: MatrixShape,
    private val cfg: GameConfig = GameConfig(trigger = Trigger.AUTO),
    sprites: ShooterSprites = ShooterSprites.defaultFor(shape),
    override val id: String = "invaders",
    private val random: Random = Random.Default,
) : Playable {
    override val needs: Set<Need> = cfg.needs

    private val ship = sprites.ship.trimmed()
    private val invA = sprites.invader.trimmed()
    private val invB = sprites.invaderAlt?.trimmed()?.takeIf { it.w == invA.w && it.h == invA.h }
    private val bossA = sprites.boss?.trimmed()
    private val bossB = sprites.bossAlt?.trimmed()?.takeIf { bossA != null && it.w == bossA.w && it.h == bossA.h }
    private val ufoSprite = sprites.ufo?.trimmed()

    private val w = shape.width
    private val h = shape.height

    // ---- layout, worked out from the matrix and the sprites
    private val shipBottom = lowestRowWithWidth(shape, max(ship.w + 4, (w * 0.65f).toInt()))
    private val shipTop = shipBottom - ship.h + 1
    private val shipRange = slideRange(shape, ship, shipTop) ?: (0..max(0, w - ship.w))
    private val slider = Slider(shipRange.first, shipRange.last, maxSpeed = w * 1.1f)
    private val livesRow = shipBottom + 1
    private val gunOffset = (0 until ship.w).firstOrNull { ship.lit(it, 0) } ?: (ship.w / 2)
    private val gapX = if (invA.w >= 3) 2 else 1
    private val cols = ((w * 0.8f + gapX) / (invA.w + gapX)).toInt().coerceIn(1, 6)
    private val strideX = invA.w + gapX
    private val strideY = invA.h + 1
    private val formationW = cols * invA.w + (cols - 1) * gapX
    private val formationTop = (0 until h).firstOrNull { shape.rowWidth(it) >= formationW + 2 } ?: 1
    private val landing = shipTop - 1

    /** Two rows of invaders when that still leaves them a few steps down, else one. */
    private val rows = if (landing - (formationTop + 2 * invA.h + 1 - 1) >= 3) 2 else 1
    private val ufoRow = max(0, formationTop - (ufoSprite?.h ?: 1) - 0)

    // ---- state
    private enum class Phase { COUNTDOWN, INTRO, PLAY, DYING, OVER }

    private var phase = Phase.COUNTDOWN
    private var phaseT = 0f
    private var score = 0
    private var lives = cfg.lives
    private var wave = 1

    private val alive = BooleanArray(cols * rows)
    private var fx0 = 0 // formation offset
    private var fy0 = 0
    private var dir = 1
    private var stepT = 0f
    private var anim = false

    private var bossWave = false
    private var bossHp = 0
    private var bossHpMax = 0
    private var bossX = 0
    private var bossY = 0
    private var bossFlash = 0f

    private class Shot(var x: Int, var y: Float, val speed: Float, val enemy: Boolean, var dead: Boolean = false)

    private val shots = ArrayList<Shot>()
    private var reload = 0f
    private var enemyFireT = 1.5f

    private var ufoX = 0f
    private var ufoDir = 1
    private var ufoActive = false
    private var ufoT = 14f

    private class Burst(val x: Int, val y: Int, var t: Float = 0f)

    private val bursts = ArrayList<Burst>()
    private var marquee: Marquee? = null
    private var overReported = false

    init {
        restart()
    }

    override fun restart() {
        score = 0
        lives = cfg.lives
        wave = 1
        slider.centre()
        overReported = false
        marquee = null
        startWave()
        phase = Phase.COUNTDOWN
        phaseT = 0f
    }

    private fun startWave() {
        shots.clear()
        bursts.clear()
        ufoActive = false
        ufoT = 10f + random.nextFloat() * 10f
        dir = 1
        stepT = 0f
        bossWave = bossA != null && wave % 4 == 0
        if (bossWave) {
            val b = bossA!!
            bossHpMax = 5 + wave
            bossHp = bossHpMax
            bossX = (w - b.w) / 2
            bossY = formationTop
        } else {
            alive.fill(true)
            fx0 = (w - formationW) / 2
            fy0 = formationTop
        }
        enemyFireT = 1.2f
        phase = Phase.INTRO
        phaseT = 0f
    }

    // ------------------------------------------------------------------ update

    override fun update(dt: Float, input: InputFrame, fx: Fx) {
        phaseT += dt
        bursts.forEach { it.t += dt }
        bursts.removeAll { it.t > 0.3f }
        when (phase) {
            Phase.COUNTDOWN -> {
                slider.update(dt, input, cfg)
                if (phaseT >= 1.5f) {
                    phase = Phase.INTRO
                    phaseT = 0.6f
                }
            }
            Phase.INTRO -> {
                slider.update(dt, input, cfg)
                if (phaseT >= 0.9f) {
                    phase = Phase.PLAY
                    phaseT = 0f
                }
            }
            Phase.PLAY -> play(dt, input, fx)
            Phase.DYING -> if (phaseT >= 1.2f) {
                lives--
                if (lives <= 0) {
                    phase = Phase.OVER
                    phaseT = 0f
                    marquee = Marquee(shape, "${cfg.labels.gameOver}  ·  $score")
                    if (!overReported) {
                        overReported = true
                        fx.gameOver(score)
                    }
                } else {
                    shots.clear()
                    phase = Phase.PLAY
                    phaseT = 0f
                }
            }
            Phase.OVER -> {
                marquee?.update(dt)
                if (phaseT > 1.2f && input.presses > 0) restart()
            }
        }
    }

    private fun play(dt: Float, input: InputFrame, fx: Fx) {
        slider.update(dt, input, cfg)
        val pace = cfg.pace

        // Player fire
        reload -= dt
        val mine = shots.count { !it.enemy }
        val maxShots = if (cfg.trigger == Trigger.AUTO) 1 else 2
        if (reload <= 0f && mine < maxShots && cfg.fired(input)) {
            shots += Shot(slider.cell + gunOffset, (shipTop - 1).toFloat(), speed = h * 1.6f, enemy = false)
            reload = if (cfg.trigger == Trigger.AUTO) 0.15f else 0.12f
        }

        // Invaders march
        if (bossWave) moveBoss(dt, pace) else moveFormation(dt, pace, fx)
        if (phase != Phase.PLAY) return

        // Enemy fire
        enemyFireT -= dt
        val maxEnemy = min(3, 1 + wave / 2) + if (bossWave) 1 else 0
        if (enemyFireT <= 0f) {
            if (shots.count { it.enemy } < maxEnemy) enemyShoot()
            enemyFireT = (1.4f * 0.9f.pow(wave - 1) / pace).coerceAtLeast(0.35f) * (0.6f + random.nextFloat() * 0.8f)
        }

        // UFO
        moveUfo(dt)

        // Shots
        for (s in shots) {
            if (s.dead) continue
            val before = s.y.toInt()
            s.y += if (s.enemy) s.speed * dt else -s.speed * dt
            val after = s.y.toInt()
            // Check every row crossed, so fast shots don't skip over a target.
            val rowsCrossed = if (s.enemy) (before + 1)..after else (before - 1) downTo after
            for (row in rowsCrossed) {
                if (hit(s, row, fx)) {
                    s.dead = true
                    break
                }
            }
            if (!s.dead && (s.y < -1f || s.y > h)) s.dead = true
        }
        // Shots cancel each other out
        for (a in shots) for (b in shots) {
            if (!a.dead && !b.dead && !a.enemy && b.enemy && a.x == b.x && abs(a.y - b.y) < 1.2f) {
                a.dead = true
                b.dead = true
                bursts += Burst(a.x, a.y.toInt())
            }
        }
        shots.removeAll { it.dead }

        // (Checked after the shots loop: a new wave clears the shot list.)
        if (phase == Phase.PLAY && if (bossWave) bossHp <= 0 else alive.none { it }) waveCleared(fx)
    }

    private fun hit(s: Shot, row: Int, fx: Fx): Boolean {
        val x = s.x
        if (s.enemy) {
            if (row in shipTop..shipBottom && ship.lit(x - slider.cell, row - shipTop)) {
                shipHit(fx)
                return true
            }
            return false
        }
        // UFO
        val u = ufoSprite
        if (ufoActive && u != null) {
            val ux = ufoX.toInt()
            if (row in ufoRow until ufoRow + u.h && u.lit(x - ux, row - ufoRow)) {
                ufoActive = false
                ufoT = 14f + random.nextFloat() * 12f
                score += 50 * (1 + random.nextInt(3))
                bursts += Burst(x, row)
                fx.buzz(Buzz.WIN)
                return true
            }
        }
        if (bossWave) {
            val b = bossA!!
            if (bossHp > 0 && row in bossY until bossY + b.h && b.lit(x - bossX, row - bossY)) {
                bossHp--
                bossFlash = 0.15f
                fx.buzz(Buzz.TICK)
                if (bossHp <= 0) {
                    score += 100 + 25 * wave
                    for (k in 0 until 4) bursts += Burst(bossX + random.nextInt(b.w), bossY + random.nextInt(b.h))
                }
                return true
            }
            return false
        }
        for (r in 0 until rows) for (c in 0 until cols) {
            val k = r * cols + c
            if (!alive[k]) continue
            val ix = fx0 + c * strideX
            val iy = fy0 + r * strideY
            if (row in iy until iy + invA.h && invSprite().lit(x - ix, row - iy)) {
                alive[k] = false
                score += if (r == 0) 30 else 20
                bursts += Burst(ix + invA.w / 2, iy + invA.h / 2)
                fx.buzz(Buzz.TICK)
                return true
            }
        }
        return false
    }

    private fun invSprite(): Sprite = if (anim && invB != null) invB else invA

    private fun moveFormation(dt: Float, pace: Float, fx: Fx) {
        val total = alive.size
        val left = alive.count { it }
        if (left == 0) return
        val interval = (0.68f / pace * 0.88f.pow(wave - 1) * (0.3f + 0.7f * left / total)).coerceAtLeast(0.07f)
        stepT += dt
        if (stepT < interval) return
        stepT = 0f
        anim = !anim
        if (formationFits(fx0 + dir, fy0)) {
            fx0 += dir
        } else {
            fy0 += 1
            dir = -dir
            if (!formationFits(fx0, fy0)) {
                // Squeezed by the round edge: step back inside on the way down.
                if (formationFits(fx0 + dir, fy0)) fx0 += dir
            }
        }
        // Landed?
        for (r in rows - 1 downTo 0) for (c in 0 until cols) {
            if (alive[r * cols + c] && fy0 + r * strideY + invA.h - 1 >= landing) {
                invaded(fx)
                return
            }
        }
    }

    private fun formationFits(ox: Int, oy: Int): Boolean {
        for (r in 0 until rows) for (c in 0 until cols) {
            if (!alive[r * cols + c]) continue
            if (!fits(shape, invA, ox + c * strideX, oy + r * strideY)) return false
        }
        return true
    }

    private fun moveBoss(dt: Float, pace: Float) {
        val b = bossA!!
        bossFlash -= dt
        val interval = (0.32f / pace * 0.95f.pow(wave - 1)).coerceAtLeast(0.08f)
        stepT += dt
        if (stepT < interval) return
        stepT = 0f
        anim = !anim
        if (fits(shape, b, bossX + dir, bossY)) {
            bossX += dir
        } else {
            dir = -dir
            if (random.nextFloat() < 0.5f && bossY + b.h < landing - 1) bossY++
        }
    }

    private fun enemyShoot() {
        if (bossWave) {
            val b = bossA!!
            val x = bossX + random.nextInt(b.w)
            shots += Shot(x, (bossY + b.h).toFloat(), enemyShotSpeed(), enemy = true)
            return
        }
        val columns = (0 until cols).filter { c -> (0 until rows).any { r -> alive[r * cols + c] } }
        if (columns.isEmpty()) return
        // Prefer a column near the ship now and then, so standing still isn't safe.
        val near = columns.minByOrNull { abs(fx0 + it * strideX + invA.w / 2 - (slider.cell + gunOffset)) }
        val c = if (near != null && random.nextFloat() < 0.4f) near else columns[random.nextInt(columns.size)]
        val r = (rows - 1 downTo 0).first { alive[it * cols + c] }
        val x = fx0 + c * strideX + invA.w / 2
        val y = fy0 + r * strideY + invA.h
        shots += Shot(x, y.toFloat(), enemyShotSpeed(), enemy = true)
    }

    private fun enemyShotSpeed(): Float = (h * 0.6f + wave * 0.4f).coerceAtMost(h * 1.1f) * cfg.pace

    private fun moveUfo(dt: Float) {
        val u = ufoSprite ?: return
        if (bossWave) return
        if (!ufoActive) {
            ufoT -= dt
            if (ufoT <= 0f) {
                ufoActive = true
                ufoDir = if (random.nextBoolean()) 1 else -1
                ufoX = if (ufoDir > 0) -u.w.toFloat() else w.toFloat()
            }
            return
        }
        ufoX += ufoDir * w * 0.45f * dt
        if (ufoX > w + 1 || ufoX < -u.w - 1) {
            ufoActive = false
            ufoT = 12f + random.nextFloat() * 12f
        }
    }

    private fun shipHit(fx: Fx) {
        phase = Phase.DYING
        phaseT = 0f
        fx.buzz(Buzz.BIG)
        bursts += Burst(slider.cell + ship.w / 2, shipTop)
    }

    private fun invaded(fx: Fx) {
        // They reached the ship: lose a life and the wave starts over.
        fx.buzz(Buzz.BIG)
        lives--
        if (lives <= 0) {
            phase = Phase.DYING
            phaseT = 1.2f // straight to game over
            lives = 1
        } else {
            startWave()
        }
    }

    private fun waveCleared(fx: Fx) {
        fx.buzz(Buzz.WIN)
        score += 10 * wave
        wave++
        startWave()
    }

    // ------------------------------------------------------------------ render

    override fun render(out: IntArray) {
        val c = Canvas(shape, out)
        when (phase) {
            Phase.COUNTDOWN -> {
                val n = 3 - (phaseT / 0.5f).toInt()
                c.centred(n.coerceIn(1, 3).toString())
                return
            }
            Phase.INTRO -> {
                if (phaseT < 0.6f) c.centred(wave.toString())
                drawShip(c, 1f)
                drawLives(c, livesRow, lives)
                return
            }
            Phase.OVER -> {
                marquee?.render(c)
                return
            }
            else -> Unit
        }

        // Invaders
        if (bossWave) {
            val b = if (anim && bossB != null) bossB else bossA!!
            c.sprite(b, bossX, bossY, if (bossFlash > 0f) 0.35f else 1f)
        } else {
            val s = invSprite()
            for (r in 0 until rows) for (col in 0 until cols) {
                if (!alive[r * cols + col]) continue
                c.sprite(s, fx0 + col * strideX, fy0 + r * strideY, if (r == 0) 1f else 0.75f)
            }
        }
        // UFO blinks
        val u = ufoSprite
        if (ufoActive && u != null) c.sprite(u, ufoX.toInt(), ufoRow, if ((phaseT * 6).toInt() % 2 == 0) 1f else 0.45f)

        // Shots
        for (s in shots) c.plot(s.x, s.y.toInt(), if (s.enemy) 130 else 255)

        // Ship (blinks while exploding)
        if (phase == Phase.DYING) {
            if ((phaseT * 10).toInt() % 2 == 0) drawShip(c, 0.6f)
        } else {
            drawShip(c, 1f)
        }
        drawLives(c, livesRow, if (phase == Phase.DYING) lives - 1 else lives)

        // Explosions
        for (b in bursts) {
            val v = if (b.t < 0.12f) 255 else 110
            if (b.t < 0.12f) c.plot(b.x, b.y, v)
            c.plot(b.x - 1, b.y - 1, v)
            c.plot(b.x + 1, b.y - 1, v)
            c.plot(b.x - 1, b.y + 1, v)
            c.plot(b.x + 1, b.y + 1, v)
        }
    }

    private fun drawShip(c: Canvas, scale: Float) = c.sprite(ship, slider.cell, shipTop, scale)

    override fun hud(): Hud = Hud(
        score = score,
        lives = lives.coerceAtLeast(0),
        level = wave,
        over = phase == Phase.OVER,
        detail = if (bossWave && phase == Phase.PLAY) "BOSS $bossHp/$bossHpMax" else null,
    )
}

package app.glyphies.play

import app.glyphies.data.AppSettings
import app.glyphies.data.Creation
import app.glyphies.data.CreationKind
import app.glyphies.data.GameTemplate
import app.glyphies.engine.AnimationPlayer
import app.glyphies.engine.BricksGame
import app.glyphies.engine.FallingGame
import app.glyphies.engine.FlyGame
import app.glyphies.engine.GameConfig
import app.glyphies.engine.HourglassToy
import app.glyphies.engine.InvadersGame
import app.glyphies.engine.Labels
import app.glyphies.engine.MazeGame
import app.glyphies.engine.Playable
import app.glyphies.engine.SandToy
import app.glyphies.engine.ShooterSprites
import app.glyphies.engine.Sprite
import app.glyphies.engine.Trigger
import app.glyphies.glyph.MatrixShape
import app.glyphies.tr

/** The animations and games that come with the app. */
enum class BuiltIn(val id: String, val isGame: Boolean) {
    SAND("sand", false),
    HOURGLASS("hourglass", false),
    INVADERS("invaders", true);

    val title: String
        get() = when (this) {
            SAND -> tr("Sand", "Sable")
            HOURGLASS -> tr("Hourglass", "Sablier")
            INVADERS -> tr("Invaders", "Envahisseurs")
        }

    val blurb: String
        get() = when (this) {
            SAND -> tr("Grains of sand that follow the tilt of the phone. Shake to throw them about.", "Des grains de sable qui suivent l'inclinaison du téléphone. Secouez pour les projeter.")
            HOURGLASS -> tr("A real timer: the sand runs through the neck one grain at a time. Turn it over to start again.", "Un vrai minuteur : le sable passe le col grain par grain. Retournez-le pour recommencer.")
            INVADERS -> tr("Space Invaders on the back of the phone. Tilt to move, the ship fires on its own.", "Space Invaders au dos du téléphone. Inclinez pour bouger, le vaisseau tire tout seul.")
        }

    val controls: String
        get() = when (this) {
            SAND -> tr("Tilt · shake · volume +/− for more or less sand", "Inclinez · secouez · volume +/− pour plus ou moins de sable")
            HOURGLASS -> tr("Stand it up · turn it over · volume key to refill", "Tenez-le debout · retournez-le · touche de volume pour le remplir")
            INVADERS -> if (app.glyphies.Graph.settings.current.autoFire) {
                tr("Tilt to move · fires on its own", "Inclinez pour bouger · tir automatique")
            } else {
                tr("Tilt to move · tap or volume key to fire", "Inclinez pour bouger · touchez ou touche de volume pour tirer")
            }
        }
}

object Catalog {
    fun labels(): Labels = Labels(
        gameOver = "GAME OVER",
        win = tr("WELL DONE", "BRAVO"),
        tapToPlay = tr("TAP TO PLAY", "TOUCHEZ POUR JOUER"),
    )

    fun builtIn(b: BuiltIn, shape: MatrixShape, s: AppSettings): Playable = when (b) {
        BuiltIn.SAND -> SandToy(shape)
        BuiltIn.HOURGLASS -> HourglassToy(shape, seconds = s.hourglassSeconds)
        BuiltIn.INVADERS -> InvadersGame(
            shape,
            GameConfig(trigger = if (s.autoFire) Trigger.AUTO else Trigger.TAP, tiltRange = s.tilt.tiltRange, labels = labels()),
        )
    }

    fun creation(c: Creation, s: AppSettings): Playable {
        val shape = c.shape
        val frames = c.decodedFrames()
        val game = c.game
        if (c.kind != CreationKind.GAME || game == null) {
            return AnimationPlayer(shape, frames, c.fps, c.loop, c.driver, id = c.id)
        }
        val cfg = GameConfig(
            move = game.move,
            trigger = game.trigger,
            speed = game.speed,
            lives = game.lives,
            tiltRange = s.tilt.tiltRange,
            labels = labels(),
        )
        val t = game.template
        fun sprite(key: String) = t.role(key)?.let { game.sprite(it, shape) }
        // Optional pictures: the default until drawn; drawn empty means "none".
        fun optional(key: String): Sprite? {
            val stored = game.sprites[key] ?: return t.role(key)?.default?.invoke(shape)
            return stored.toSprite().takeUnless { it.isEmpty }
        }
        return when (t) {
            GameTemplate.SHOOTER -> {
                val defaults = ShooterSprites.defaultFor(shape)
                val custom = game.sprites.isNotEmpty()
                InvadersGame(
                    shape, cfg,
                    ShooterSprites(
                        ship = sprite("ship")!!,
                        invader = sprite("invader")!!,
                        // A drawn invader doesn't walk with the built-in second step.
                        invaderAlt = if ("invader" in game.sprites && "invader2" !in game.sprites) null else optional("invader2"),
                        // Boss and UFO only with the built-in pictures (they'd clash with drawn ones).
                        boss = if (custom) null else defaults.boss,
                        bossAlt = if (custom) null else defaults.bossAlt,
                        ufo = defaults.ufo,
                    ),
                    id = c.id,
                )
            }
            GameTemplate.DODGE -> FallingGame(shape, catch = false, player = sprite("player")!!, item = sprite("obstacle")!!, cfg = cfg, id = c.id)
            GameTemplate.CATCH -> FallingGame(shape, catch = true, player = sprite("player")!!, item = sprite("item")!!, bomb = optional("bomb"), cfg = cfg, id = c.id)
            GameTemplate.MAZE -> MazeGame(shape, frames, cfg, id = c.id)
            GameTemplate.FLY -> FlyGame(shape, sprite("bird")!!, cfg, id = c.id)
            GameTemplate.BRICKS -> BricksGame(shape, frames, sprite("paddle"), cfg, id = c.id)
        }
    }
}

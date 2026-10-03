package app.glyphies.ui.home

import app.glyphies.Graph
import app.glyphies.data.AppSettings
import app.glyphies.data.Creation
import app.glyphies.data.CreationKind
import app.glyphies.data.GameTemplate
import app.glyphies.data.label
import app.glyphies.engine.AnalogClock
import app.glyphies.engine.Driver
import app.glyphies.engine.EmojiToy
import app.glyphies.engine.GameConfig
import app.glyphies.engine.HourglassToy
import app.glyphies.engine.InvadersGame
import app.glyphies.engine.Move
import app.glyphies.engine.NoFx
import app.glyphies.engine.Playable
import app.glyphies.engine.SandToy
import app.glyphies.engine.Trigger
import app.glyphies.glyph.EmojiRaster
import app.glyphies.glyph.GlyphSupport
import app.glyphies.glyph.MatrixShape
import app.glyphies.isFrench
import app.glyphies.play.BuiltIn
import app.glyphies.play.Catalog
import app.glyphies.tr
import app.glyphies.ui.components.Thumbs
import app.glyphies.ui.components.demoInput
import app.glyphies.ui.duration

enum class Category { GAME, ANIMATION }

/** One tile of the library: a built-in or one of your creations. */
class Entry private constructor(
    val key: String,
    val category: Category,
    val title: String,
    val kindLabel: String,
    val subtitle: String,
    val shape: MatrixShape,
    val builtIn: BuiltIn?,
    val creation: Creation?,
) {
    val mine: Boolean get() = creation != null

    /** A copy of the thing that plays on its own (games steer by "finger" so they move). */
    fun preview(settings: AppSettings): Playable? = builtIn?.let { previewOf(it, shape, settings) }
        ?: creation?.let { previewOf(it, settings) }

    /** A still picture, for when previews don't play. */
    fun still(settings: AppSettings): IntArray = creation?.let { Thumbs.of(it) } ?: run {
        val p = preview(settings) ?: return shape.blank()
        var t = 0f
        repeat(30) {
            t += 0.1f
            p.update(0.1f, demoInput(t, 0.1f), NoFx)
        }
        shape.blank().also { p.render(it) }
    }

    fun play() {
        if (builtIn != null) Graph.player.play(builtIn) else creation?.let { Graph.player.play(it.id) }
    }

    companion object {
        fun of(b: BuiltIn, shape: MatrixShape, s: AppSettings) = Entry(
            key = "builtin:${b.name}",
            category = if (b.isGame) Category.GAME else Category.ANIMATION,
            title = b.title,
            kindLabel = if (b.isGame) tr("Game", "Jeu") else tr("Animation", "Animation"),
            subtitle = when (b) {
                BuiltIn.SAND -> tr("Tilt · shake", "Incliner · secouer")
                BuiltIn.HOURGLASS -> tr("Timer · ${duration(s.hourglassSeconds)}", "Minuteur · ${duration(s.hourglassSeconds)}")
                BuiltIn.CLOCK -> tr("Time · face down", "Heure · face cachée")
                BuiltIn.EMOJI -> "${s.emoji} · " + tr("tap to pick", "toucher pour choisir")
                BuiltIn.INVADERS -> if (s.autoFire) tr("Tilt · auto-fire", "Incliner · tir auto") else tr("Tilt · tap to fire", "Incliner · toucher pour tirer")
            },
            shape = shape,
            builtIn = b,
            creation = null,
        )

        fun of(c: Creation) = Entry(
            key = c.id,
            category = if (c.kind == CreationKind.GAME) Category.GAME else Category.ANIMATION,
            title = c.name,
            kindLabel = when (c.kind) {
                CreationKind.GAME -> tr("Game", "Jeu")
                CreationKind.ANIMATION -> tr("Animation", "Animation")
                CreationKind.DRAWING -> tr("Drawing", "Dessin")
            },
            subtitle = subtitleOf(c),
            shape = c.shape,
            builtIn = null,
            creation = c,
        )

        /** What a creation is played with, in a few words. */
        fun subtitleOf(c: Creation): String {
            val g = c.game
            if (g != null) {
                val how = when (g.template) {
                    GameTemplate.MAZE -> Move.TILT.label
                    GameTemplate.FLY -> g.trigger.label
                    else -> g.move.label
                }
                return "${g.template.label} · $how"
            }
            return when {
                c.driver == Driver.TIME && c.frames.size <= 1 -> tr("Still", "Image fixe")
                c.driver == Driver.TIME -> tr("Time · ${c.fps} fps", "Temps · ${c.fps} img/s")
                else -> c.driver.label
            }
        }

        fun previewOf(b: BuiltIn, shape: MatrixShape, s: AppSettings): Playable = when (b) {
            BuiltIn.SAND -> SandToy(shape)
            BuiltIn.HOURGLASS -> HourglassToy(shape, seconds = 20)
            BuiltIn.CLOCK -> AnalogClock(shape)
            BuiltIn.EMOJI -> {
                // The preview goes through the set on its own.
                var shown = s.emoji
                EmojiToy(shape, selected = { shown }, onSelect = { shown = it }, raster = EmojiRaster::render, french = isFrench)
            }
            BuiltIn.INVADERS -> InvadersGame(shape, GameConfig(move = Move.TOUCH, trigger = Trigger.AUTO))
        }

        fun previewOf(c: Creation, s: AppSettings): Playable? = runCatching {
            val g = c.game
            val demo = if (g == null) c else c.copy(
                game = g.copy(
                    move = Move.TOUCH,
                    trigger = when (g.template) {
                        GameTemplate.FLY -> Trigger.MIC
                        GameTemplate.SHOOTER, GameTemplate.BRICKS -> Trigger.AUTO
                        else -> g.trigger
                    },
                ),
            )
            Catalog.creation(demo, s)
        }.getOrNull()

        /** Everything for the home screen: built-ins first, then yours (newest first). */
        fun all(creations: List<Creation>, s: AppSettings): List<Entry> {
            val shape = GlyphSupport.displayShape(s.previewSize)
            return BuiltIn.entries.map { of(it, shape, s) } + creations.map { of(it) }
        }
    }
}

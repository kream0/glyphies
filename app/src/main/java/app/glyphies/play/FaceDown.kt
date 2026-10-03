package app.glyphies.play

import app.glyphies.Graph
import app.glyphies.data.Creation
import app.glyphies.data.CreationKind
import app.glyphies.engine.AnalogClock
import app.glyphies.engine.AnimationPlayer
import app.glyphies.engine.Driver
import app.glyphies.engine.HourglassToy
import app.glyphies.engine.Playable
import app.glyphies.engine.SandToy
import app.glyphies.glyph.GlyphSupport
import app.glyphies.glyph.MatrixShape
import app.glyphies.tr

/**
 * What the Glyphies Glyph Toy shows on the back with the app closed: on the Phone (4a) Pro
 * when it lies face down (Flip to Glyph, in place of Nothing's clock), on the Phone (3) in
 * the Glyph Button carousel. The analog clock unless you pick something else.
 */
object FaceDown {
    const val DEFAULT = "builtin:CLOCK"

    fun key(b: BuiltIn): String = "builtin:${b.name}"

    val current: String get() = Graph.settings.current.toy ?: DEFAULT

    /** Games need you holding the phone, so they can't sit on the back on their own. */
    fun canShow(b: BuiltIn): Boolean = !b.isGame
    fun canShow(c: Creation): Boolean = c.kind != CreationKind.GAME

    private fun builtIn(key: String): BuiltIn? =
        key.removePrefix("builtin:").takeIf { key.startsWith("builtin:") }?.let { n -> BuiltIn.entries.firstOrNull { it.name == n } }

    /** Display name of a choice ("Clock", or the creation's name). */
    fun name(key: String): String {
        val b = builtIn(key) ?: return Graph.creations.get(key)?.name ?: BuiltIn.CLOCK.title
        return if (b == BuiltIn.EMOJI) "${b.title} ${Graph.settings.current.emoji}" else b.title
    }

    /** Everything that can go on the back. */
    fun choices(): List<String> =
        BuiltIn.entries.filter { canShow(it) }.map { key(it) } +
            Graph.creations.all.value.filter { canShow(it) }.map { it.id }

    /** What plays on the back for [key] (falls back to the clock if a creation was deleted). */
    fun playable(key: String, device: MatrixShape): Playable {
        when (builtIn(key)) {
            BuiltIn.SAND -> return SandToy(device, fill = 0.38f)
            BuiltIn.HOURGLASS -> return HourglassToy(device, seconds = Graph.settings.current.hourglassSeconds)
            BuiltIn.CLOCK -> return AnalogClock(device)
            BuiltIn.EMOJI -> return Catalog.emojiToy(device, interactive = false)
            BuiltIn.INVADERS, null -> Unit
        }
        val c = Graph.creations.get(key)?.takeIf { canShow(it) } ?: return AnalogClock(device)
        // No sensors to drive it face down: animations simply play in time.
        return AnimationPlayer(c.shape, c.decodedFrames(), c.fps, c.loop, Driver.TIME, id = c.id)
    }

    fun set(key: String) {
        Graph.settings.update { it.copy(toy = key) }
    }

    /** Where to turn it on, in the system settings (once). */
    val howTo: String
        get() = if (GlyphSupport.hasGlyphTouch) {
            tr(
                "On the Phone (3), add Glyphies to the Glyph Button carousel (Settings › Glyph Interface › Glyph Toys), then pick it with a short press.",
                "Sur le Phone (3), ajoutez Glyphies au carrousel du Glyph Button (Paramètres › Glyph Interface › Glyph Toys), puis choisissez-le d'un appui court.",
            )
        } else {
            tr(
                "Put the phone face down to see it. If Nothing's clock still shows, choose Glyphies once in Settings › Glyph Interface › Flip to Glyph › Always-on Glyph Toy.",
                "Retournez le téléphone pour le voir. Si l'horloge de Nothing s'affiche encore, choisissez une fois Glyphies dans Paramètres › Glyph Interface › Flip to Glyph › Always-on Glyph Toy.",
            )
        }
}

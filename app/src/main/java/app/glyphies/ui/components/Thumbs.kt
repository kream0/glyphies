package app.glyphies.ui.components

import app.glyphies.Graph
import app.glyphies.data.Creation
import app.glyphies.data.CreationKind
import app.glyphies.engine.InputFrame
import app.glyphies.engine.Marks
import app.glyphies.engine.NoFx
import app.glyphies.play.Catalog

/** A still picture of a creation for lists: its first frame, or a game a couple of seconds in. */
object Thumbs {
    private val cache = HashMap<String, IntArray>()

    fun of(c: Creation): IntArray {
        val key = "${c.id}:${c.updated}:${c.frames.hashCode()}:${c.game.hashCode()}"
        cache[key]?.let { return it }
        val frame = if (c.kind == CreationKind.GAME) {
            runCatching {
                val p = Catalog.creation(c, Graph.settings.current)
                val input = InputFrame(gx = 0.2f, gy = 1f, touchX = 0.6f, mic = 0.4f)
                repeat(26) { p.update(0.1f, input, NoFx) }
                c.shape.blank().also { p.render(it) }
            }.getOrElse { c.frame(0) }
        } else {
            Marks.strip(c.frame(0))
        }
        if (cache.size > 200) cache.clear()
        cache[key] = frame
        return frame
    }
}

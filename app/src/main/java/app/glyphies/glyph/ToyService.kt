package app.glyphies.glyph

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import app.glyphies.Graph
import app.glyphies.data.CreationKind
import app.glyphies.engine.AnimationPlayer
import app.glyphies.engine.Driver
import app.glyphies.engine.InputFrame
import app.glyphies.engine.LoopMode
import app.glyphies.engine.NoFx
import app.glyphies.engine.Playable
import app.glyphies.engine.Sprite
import com.nothing.ketchum.GlyphToy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The walking invader: the app's mascot and the Glyph Toy when nothing else is chosen. */
object Mascot {
    private val a = Sprite.parse(
        "..#.....#..",
        "...#...#...",
        "..#######..",
        ".##.###.##.",
        "###########",
        "#.#######.#",
        "#.#.....#.#",
        "...##.##...",
    )
    private val b = Sprite.parse(
        "..#.....#..",
        "#..#...#..#",
        "#.#######.#",
        "###.###.###",
        "###########",
        ".#########.",
        "..#.....#..",
        ".#.......#.",
    )

    fun frames(shape: MatrixShape): List<IntArray> = listOf(a, b).map { s ->
        val out = shape.blank()
        val x0 = (shape.width - s.w) / 2
        val y0 = (shape.height - s.h) / 2
        for (y in 0 until s.h) for (x in 0 until s.w) {
            if (s.lit(x, y) && shape.isLed(x0 + x, y0 + y)) out[(y0 + y) * shape.width + x0 + x] = 255
        }
        out
    }

    fun playable(shape: MatrixShape): Playable =
        AnimationPlayer(shape, frames(shape), fps = 2, loop = LoopMode.LOOP, driver = Driver.TIME, id = "mascot")
}

/**
 * "Glyphies" Glyph Toy: plays the drawing or animation chosen in the app (the walking invader
 * otherwise) on the Glyph Matrix, even with the app closed.
 *
 * - Phone (4a) Pro: always-on toys only. Settings › Glyph Interface › Flip to Glyph ›
 *   Always-on Glyph Toy › Glyphies; the system sends EVENT_AOD every minute.
 * - Phone (3): add it to the Glyph Button carousel; a long press restarts the animation.
 */
class ToyService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: GlyphSession? = null
    private val jobs = ArrayList<Job>()
    private var playable: Playable? = null

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) {
                super.handleMessage(msg)
                return
            }
            val event = msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA) ?: return
            GlyphLink.toyEvent(event)
            when (event) {
                GlyphToy.EVENT_CHANGE -> playable?.restart() // long press on the Glyph Button
                GlyphToy.EVENT_AOD -> session?.refresh() // always-on wake-up: repaint now
            }
        }
    }
    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder? {
        if (GlyphSupport.isSupported && jobs.isEmpty()) {
            val s = GlyphSession(this, toyMode = true).also { it.open() }
            session = s
            val device = MatrixShape.forSize(GlyphSupport.matrixSize)
            jobs += scope.launch {
                // Rebuild whenever the chosen creation (or its content) changes.
                combine(
                    Graph.settings.state.map { it.toyCreation }.distinctUntilChanged(),
                    Graph.creations.all,
                ) { id, all -> all.firstOrNull { it.id == id } }
                    .distinctUntilChanged()
                    .collect { creation ->
                        playable = creation
                            ?.takeIf { it.kind != CreationKind.GAME }
                            ?.let { c ->
                                // No sensors here: sensor-driven animations simply play in time.
                                AnimationPlayer(c.shape, c.decodedFrames(), c.fps, c.loop, Driver.TIME, id = c.id)
                            }
                            ?: Mascot.playable(device)
                    }
            }
            jobs += scope.launch {
                var last = SystemClock.elapsedRealtime()
                while (isActive) {
                    val p = playable
                    val now = SystemClock.elapsedRealtime()
                    if (p != null) {
                        p.update(((now - last) / 1000f).coerceAtMost(0.5f), InputFrame(), NoFx)
                        val frame = p.shape.blank()
                        p.render(frame)
                        s.show(device.mask(Frames.fit(frame, p.shape.width, p.shape.height, device.width, device.height)))
                    }
                    last = now
                    delay(80)
                }
            }
        }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        jobs.forEach { it.cancel() }
        jobs.clear()
        playable = null
        session?.close()
        session = null
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

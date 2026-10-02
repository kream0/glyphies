package app.glyphies.play

import android.content.Context
import android.os.SystemClock
import app.glyphies.Graph
import app.glyphies.engine.Buzz
import app.glyphies.engine.Fx
import app.glyphies.engine.Hud
import app.glyphies.engine.Need
import app.glyphies.engine.Playable
import app.glyphies.glyph.GlyphOutput
import app.glyphies.glyph.GlyphSupport
import app.glyphies.glyph.MatrixShape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What's playing, for the player screen. A new [frame] arrives every tick. */
data class PlayState(
    val title: String,
    val shape: MatrixShape,
    val frame: IntArray,
    val hud: Hud,
    val needs: Set<Need>,
    val isGame: Boolean,
    val scoreId: String,
    val best: Int,
    val newBest: Boolean = false,
    val paused: Boolean = false,
    val builtIn: BuiltIn? = null,
    val creationId: String? = null,
)

/**
 * Runs one [Playable] at a time: reads the sensors, advances it, draws it, and sends the frame
 * to the Glyph Matrix and the screen. Main thread.
 */
class Player(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<PlayState?>(null)
    val state: StateFlow<PlayState?> = _state.asStateFlow()

    private var playable: Playable? = null
    private var job: Job? = null
    private var hidden = false

    val isActive: Boolean get() = _state.value != null

    private val fx = object : Fx {
        override fun buzz(kind: Buzz) = Graph.haptics.buzz(kind)

        override fun gameOver(score: Int) {
            val s = _state.value ?: return
            val record = Graph.scores.submit(s.scoreId, score)
            _state.value = s.copy(best = Graph.scores.best(s.scoreId), newBest = record)
        }
    }

    fun play(builtIn: BuiltIn) {
        val settings = Graph.settings.current
        val p = Catalog.builtIn(builtIn, GlyphSupport.displayShape(settings.previewSize), settings)
        start(builtIn.title, p, isGame = builtIn.isGame, builtIn = builtIn)
        Graph.settings.update { it.copy(lastPlayed = "builtin:${builtIn.name}") }
    }

    fun play(creationId: String) {
        val c = Graph.creations.get(creationId) ?: return
        val p = Catalog.creation(c, Graph.settings.current)
        start(c.name, p, isGame = c.game != null, creationId = c.id)
        Graph.settings.update { it.copy(lastPlayed = c.id) }
    }

    private fun start(title: String, p: Playable, isGame: Boolean, builtIn: BuiltIn? = null, creationId: String? = null) {
        stop()
        playable = p
        _state.value = PlayState(
            title = title,
            shape = p.shape,
            frame = p.shape.blank(),
            hud = p.hud(),
            needs = p.needs,
            isGame = isGame,
            scoreId = p.id,
            best = Graph.scores.best(p.id),
            builtIn = builtIn,
            creationId = creationId,
        )
        hidden = false
        run()
    }

    private fun run() {
        val p = playable ?: return
        val settings = Graph.settings.current
        Graph.sensors.start(p.needs, settings.facing, settings.mic.micBoostDb)
        job?.cancel()
        job = scope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                val dt = ((now - last) / 1000f).coerceIn(0f, 0.1f)
                last = now
                val s = _state.value ?: break
                val input = Graph.sensors.snapshot()
                if (!s.paused) p.update(dt, input, fx)
                val frame = p.shape.blank()
                p.render(frame)
                GlyphOutput.show(context, frame, p.shape)
                _state.value = (_state.value ?: break).copy(frame = frame, hud = p.hud())
                val spent = SystemClock.elapsedRealtime() - now
                delay((p.frameMs - spent).coerceAtLeast(4L))
            }
        }
    }

    /** Sensors again with the current settings (e.g. after the microphone was allowed). */
    fun refreshSensors() {
        val p = playable ?: return
        val settings = Graph.settings.current
        Graph.sensors.start(p.needs, settings.facing, settings.mic.micBoostDb)
    }

    fun togglePause() {
        _state.value = _state.value?.let { it.copy(paused = !it.paused) }
    }

    fun restart() {
        playable?.restart()
        _state.value = _state.value?.copy(paused = false, newBest = false)
    }

    fun stop() {
        job?.cancel()
        job = null
        playable = null
        _state.value = null
        Graph.sensors.stop()
        GlyphOutput.release()
    }

    /** The app went to the background: pause and let go of the sensors and the matrix. */
    fun onHidden() {
        if (_state.value == null) return
        hidden = true
        job?.cancel()
        job = null
        _state.value = _state.value?.copy(paused = true)
        Graph.sensors.stop()
        GlyphOutput.release()
    }

    fun onShown() {
        if (!hidden || playable == null) return
        hidden = false
        run()
    }
}

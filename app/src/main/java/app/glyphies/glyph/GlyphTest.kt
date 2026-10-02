package app.glyphies.glyph

import android.content.Context
import android.os.SystemClock
import app.glyphies.engine.Canvas
import app.glyphies.engine.Marquee
import app.glyphies.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Settings › Glyph Matrix › Test: a few seconds of scrolling text through the app channel, so
 * the whole path (service, registration, frames) can be checked. Other output holds off meanwhile.
 */
object GlyphTest {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    suspend fun run(context: Context, durationMs: Long = 8_000L) {
        if (_running.value || !GlyphSupport.isSupported) return
        _running.value = true
        val shape = MatrixShape.forSize(GlyphSupport.matrixSize)
        val session = GlyphSession(context, toyMode = false)
        val marquee = Marquee(shape, tr("GLYPHIES  ·  GLYPH TEST", "GLYPHIES  ·  TEST GLYPH"))
        try {
            withTimeoutOrNull(durationMs) {
                var last = SystemClock.elapsedRealtime()
                while (true) {
                    val now = SystemClock.elapsedRealtime()
                    marquee.update((now - last) / 1000f)
                    last = now
                    val frame = shape.blank()
                    marquee.render(Canvas(shape, frame))
                    session.show(frame)
                    delay(60)
                }
            }
        } finally {
            session.close()
            _running.value = false
        }
    }
}

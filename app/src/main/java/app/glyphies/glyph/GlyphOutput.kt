package app.glyphies.glyph

import android.content.Context
import app.glyphies.Graph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's frames on the Glyph Matrix (the "app" channel): whatever is playing or being drawn.
 * Fits each frame to this phone's matrix, applies the brightness setting and skips the matrix
 * when it's switched off in Settings or absent. Main thread only.
 */
object GlyphOutput {
    private var session: GlyphSession? = null

    private val _sending = MutableStateFlow(false)

    /** True while frames are going to the back of the phone. */
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    /** Whether frames would reach a real matrix right now. */
    val available: Boolean
        get() = GlyphSupport.isSupported && Graph.settings.current.glyphOutput

    fun show(context: Context, frame: IntArray, shape: MatrixShape) {
        if (!available || GlyphTest.running.value) {
            if (session != null) release()
            return
        }
        val device = MatrixShape.forSize(GlyphSupport.matrixSize)
        val fitted = device.mask(Frames.fit(frame, shape.width, shape.height, device.width, device.height))
        val percent = Graph.settings.current.brightness
        if (percent < 100) {
            for (i in fitted.indices) {
                val v = fitted[i]
                if (v > 0) fitted[i] = (v * percent / 100).coerceAtLeast(1)
            }
        }
        val s = session ?: GlyphSession(context, toyMode = false).also {
            session = it
            it.open()
        }
        s.show(fitted)
        _sending.value = true
    }

    /** Turns the matrix off but keeps the link (e.g. between two games). */
    fun clear() {
        session?.clear()
        _sending.value = false
    }

    /** Turns the matrix off and lets go of Nothing's Glyph service. */
    fun release() {
        session?.close()
        session = null
        _sending.value = false
    }
}

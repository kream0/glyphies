package app.glyphies

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.glyphies.data.ThemeMode
import app.glyphies.glyph.GlyphOutput
import app.glyphies.ui.AppRoot
import app.glyphies.ui.AppViewModel
import app.glyphies.ui.theme.DarkPalette
import app.glyphies.ui.theme.GlyphiesTheme
import app.glyphies.ui.theme.LightPalette

class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        applySystemBars(isDark(Graph.settings.current.theme, systemDark()))
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        val language = Graph.settings.current.language
        setContent {
            val settings by Graph.settings.state.collectAsStateWithLifecycle()
            val dark = isDark(settings.theme, isSystemInDarkTheme())
            LaunchedEffect(dark) { applySystemBars(dark) }
            // UI text is read in the chosen language (see tr()): start over in the new one.
            LaunchedEffect(settings.language) { if (settings.language != language) recreate() }
            GlyphiesTheme(dark) {
                AppRoot(appViewModel)
            }
        }
    }

    private fun systemDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun isDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.PAPER -> false
    }

    /** Status / navigation bar icons that read on the chosen theme, and a matching window behind it. */
    private fun applySystemBars(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        window.setBackgroundDrawable(ColorDrawable((if (dark) DarkPalette else LightPalette).background.toArgb()))
    }

    override fun onStart() {
        super.onStart()
        Graph.updater.checkIfDue()
        Graph.player.onShown()
    }

    override fun onStop() {
        super.onStop()
        Graph.player.onHidden()
        GlyphOutput.release()
    }

    /** While something plays, the volume keys are buttons (they're under your fingers). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if ((code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            Graph.player.isActive && Graph.settings.current.volumeKeys
        ) {
            val plus = code == KeyEvent.KEYCODE_VOLUME_UP
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) Graph.sensors.volumeKey(plus, pressed = true)
                KeyEvent.ACTION_UP -> Graph.sensors.volumeKey(plus, pressed = false)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_INSTALL_STATUS -> Graph.updater.onInstallStatus(this, intent)
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "app.glyphies.INSTALL_STATUS"
    }
}

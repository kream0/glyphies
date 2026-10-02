package app.glyphies.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import app.glyphies.tr

enum class Tab(private val en: String, private val fr: String) {
    PLAY("PLAY", "JOUER"),
    CREATE("CREATE", "CRÉER"),
    SETTINGS("SETTINGS", "RÉGLAGES");

    val label: String get() = tr(en, fr)
}

/** App-level navigation state (survives configuration changes). */
class AppViewModel : ViewModel() {
    var tab by mutableStateOf(Tab.PLAY)

    /** The creation open in the editor, if any. */
    var editing by mutableStateOf<String?>(null)

    fun selectTab(t: Tab) {
        tab = t
    }

    fun edit(id: String) {
        editing = id
    }
}

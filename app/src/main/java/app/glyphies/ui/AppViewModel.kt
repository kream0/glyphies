package app.glyphies.ui

import androidx.compose.runtime.getValue
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import app.glyphies.Graph
import app.glyphies.tr

/** The three main tabs: the library of games and animations, the editor, the settings. */
enum class Tab(private val en: String, private val fr: String) {
    HOME("HOME", "ACCUEIL"),
    EDITOR("EDITOR", "ÉDITEUR"),
    SETTINGS("SETTINGS", "RÉGLAGES");

    val label: String get() = tr(en, fr)
}

/** Filters of the home library. */
enum class HomeFilter(private val en: String, private val fr: String) {
    ALL("ALL", "TOUT"),
    GAMES("GAMES", "JEUX"),
    ANIMATIONS("ANIMATIONS", "ANIMATIONS"),
    MINE("MINE", "LES MIENS");

    val label: String get() = tr(en, fr)
}

/** App-level navigation state (survives configuration changes). */
class AppViewModel : ViewModel() {
    var tab by mutableStateOf(Tab.HOME)
    var homeFilter by mutableStateOf(HomeFilter.ALL)

    /** Scroll positions, kept while switching tabs. */
    val homeGrid = LazyGridState()
    val galleryGrid = LazyGridState()

    /** The creation open in the editor tab (remembered across launches); null = its gallery. */
    var editing by mutableStateOf(Graph.settings.current.lastEdited)
        private set

    fun selectTab(t: Tab) {
        tab = t
    }

    /** Opens [id] in the editor tab. */
    fun openEditor(id: String) {
        editing = id
        Graph.settings.update { it.copy(lastEdited = id) }
        tab = Tab.EDITOR
    }

    /** Back to the editor's gallery. */
    fun closeEditor() {
        editing = null
        Graph.settings.update { it.copy(lastEdited = null) }
    }
}

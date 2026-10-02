package app.glyphies.data

import android.content.Context
import app.glyphies.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The app's language; French unless you pick English (Settings → Appearance). */
enum class Language(val label: String) {
    FRENCH("FRANÇAIS"),
    ENGLISH("ENGLISH"),
}

enum class ThemeMode(
    private val labelEn: String,
    private val labelFr: String,
    private val descriptionEn: String,
    private val descriptionFr: String,
) {
    SYSTEM(
        "SYSTEM",
        "SYSTÈME",
        "Follows the phone's dark mode: black when it's on, paper when it's off.",
        "Suit le mode sombre du téléphone : noir s'il est activé, papier sinon.",
    ),
    DARK("DARK", "SOMBRE", "Black, like Nothing OS.", "Noir, comme Nothing OS."),
    PAPER("PAPER", "PAPIER", "Warm paper instead of white, with black ink.", "Un papier chaud plutôt que du blanc, à l'encre noire.");

    val label: String get() = tr(labelEn, labelFr)
    val description: String get() = tr(descriptionEn, descriptionFr)
}

/** Which side of the phone you look at while playing: it decides which way "left" is. */
enum class Facing(
    private val labelEn: String,
    private val labelFr: String,
    private val descriptionEn: String,
    private val descriptionFr: String,
) {
    BACK(
        "GLYPH MATRIX",
        "GLYPH MATRIX",
        "You watch the back of the phone: tilting moves things the way you see them on the matrix.",
        "Vous regardez le dos du téléphone : l'inclinaison suit ce que vous voyez sur la matrice.",
    ),
    SCREEN(
        "SCREEN",
        "ÉCRAN",
        "You watch the screen: tilting follows the matrix shown on the screen (the back is mirrored).",
        "Vous regardez l'écran : l'inclinaison suit la matrice affichée à l'écran (le dos est inversé).",
    ),
    ;

    val label: String get() = tr(labelEn, labelFr)
    val description: String get() = tr(descriptionEn, descriptionFr)
}

enum class Sensitivity(private val labelEn: String, private val labelFr: String, val tiltRange: Float, val micBoostDb: Float) {
    LOW("LOW", "FAIBLE", 0.6f, -8f),
    MEDIUM("MEDIUM", "MOYENNE", 0.42f, 0f),
    HIGH("HIGH", "FORTE", 0.26f, 10f);

    val label: String get() = tr(labelEn, labelFr)
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: Language = Language.FRENCH,
    val autoUpdate: Boolean = true,
    /** Send what plays to the Glyph Matrix (otherwise it only shows on the screen). */
    val glyphOutput: Boolean = true,
    /** Matrix brightness, 25..100 %. */
    val brightness: Int = 100,
    val facing: Facing = Facing.BACK,
    val tilt: Sensitivity = Sensitivity.MEDIUM,
    val mic: Sensitivity = Sensitivity.MEDIUM,
    /** Invaders fire on their own; off = tap / volume key to fire. */
    val autoFire: Boolean = true,
    /** Volume keys act as buttons while something plays. */
    val volumeKeys: Boolean = true,
    val haptics: Boolean = true,
    /** Matrix shown on phones without one: 13 (Phone (4a) Pro) or 25 (Phone (3)). */
    val previewSize: Int = 13,
    /**
     * What the Glyphies Glyph Toy shows (face down on the (4a) Pro, in the Glyph Button carousel
     * on the Phone (3)): "builtin:CLOCK", "builtin:SAND"… or a creation id. Null = the clock.
     */
    val toy: String? = null,
    /** The how-to for Flip to Glyph has been shown once. */
    val toyGuideShown: Boolean = false,
    val hourglassSeconds: Int = 60,
    /** Tiles on the home screen play a live preview (off: a still picture, saves battery). */
    val animatedTiles: Boolean = true,
    /** What was played last ("builtin:SAND" or a creation id), for "Continue" on the home screen. */
    val lastPlayed: String? = null,
    /** The creation open in the editor tab, so it comes back where you left it. */
    val lastEdited: String? = null,
)

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("glyphies_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    fun update(block: (AppSettings) -> AppSettings) {
        val next = block(_state.value)
        _state.value = next
        save(next)
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = enumOr(prefs.getString("theme", null), d.theme),
            language = enumOr(prefs.getString("language", null), d.language),
            autoUpdate = prefs.getBoolean("autoUpdate", d.autoUpdate),
            glyphOutput = prefs.getBoolean("glyphOutput", d.glyphOutput),
            brightness = prefs.getInt("brightness", d.brightness).coerceIn(25, 100),
            facing = enumOr(prefs.getString("facing", null), d.facing),
            tilt = enumOr(prefs.getString("tilt", null), d.tilt),
            mic = enumOr(prefs.getString("mic", null), d.mic),
            autoFire = prefs.getBoolean("autoFire", d.autoFire),
            volumeKeys = prefs.getBoolean("volumeKeys", d.volumeKeys),
            haptics = prefs.getBoolean("haptics", d.haptics),
            previewSize = prefs.getInt("previewSize", d.previewSize).let { if (it == 25) 25 else 13 },
            toy = prefs.getString("toy", null) ?: prefs.getString("toyCreation", null),
            toyGuideShown = prefs.getBoolean("toyGuideShown", d.toyGuideShown),
            hourglassSeconds = prefs.getInt("hourglassSeconds", d.hourglassSeconds).coerceIn(10, 3600),
            animatedTiles = prefs.getBoolean("animatedTiles", d.animatedTiles),
            lastPlayed = prefs.getString("lastPlayed", null),
            lastEdited = prefs.getString("lastEdited", null),
        )
    }

    private fun save(s: AppSettings) {
        prefs.edit()
            .putString("theme", s.theme.name)
            .putString("language", s.language.name)
            .putBoolean("autoUpdate", s.autoUpdate)
            .putBoolean("glyphOutput", s.glyphOutput)
            .putInt("brightness", s.brightness)
            .putString("facing", s.facing.name)
            .putString("tilt", s.tilt.name)
            .putString("mic", s.mic.name)
            .putBoolean("autoFire", s.autoFire)
            .putBoolean("volumeKeys", s.volumeKeys)
            .putBoolean("haptics", s.haptics)
            .putInt("previewSize", s.previewSize)
            .putString("toy", s.toy)
            .putBoolean("toyGuideShown", s.toyGuideShown)
            .putInt("hourglassSeconds", s.hourglassSeconds)
            .putBoolean("animatedTiles", s.animatedTiles)
            .putString("lastPlayed", s.lastPlayed)
            .putString("lastEdited", s.lastEdited)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback
}

/** Best score per game, kept on the phone. */
class Scores(context: Context) {
    private val prefs = context.getSharedPreferences("glyphies_scores", Context.MODE_PRIVATE)
    private val _best = MutableStateFlow(prefs.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap())
    val best: StateFlow<Map<String, Int>> = _best.asStateFlow()

    fun best(id: String): Int = _best.value[id] ?: 0

    /** Records [score]; returns true when it's a new best. */
    fun submit(id: String, score: Int): Boolean {
        if (score <= best(id)) return false
        prefs.edit().putInt(id, score).apply()
        _best.value = _best.value + (id to score)
        return true
    }

    fun forget(id: String) {
        prefs.edit().remove(id).apply()
        _best.value = _best.value - id
    }
}

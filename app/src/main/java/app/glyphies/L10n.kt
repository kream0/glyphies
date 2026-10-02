package app.glyphies

import app.glyphies.data.Language

/**
 * The app speaks French (the default) or English, picked in Settings → Appearance → Language.
 * Each piece of UI text is written in both languages where it's used:
 * `tr("Play", "Jouer")`. Changing the language recreates the activity, so everything
 * on screen is read again.
 */
fun tr(en: String, fr: String): String = if (isFrench) fr else en

val isFrench: Boolean get() = Graph.settings.current.language == Language.FRENCH

/**
 * "[n] [word]" with the right plural: "1 frame" / "0 frames" / "3 frames" in English,
 * "0 image" / "1 image" / "3 images" in French (0 and 1 are singular there).
 */
fun trCount(n: Int, enOne: String, enMany: String, frOne: String, frMany: String): String =
    if (isFrench) "$n ${if (n <= 1) frOne else frMany}" else "$n ${if (n == 1) enOne else enMany}"

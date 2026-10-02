package app.glyphies.ui

/** "30 s", "1 min", "1 min 30 s". */
fun duration(seconds: Int): String = when {
    seconds < 60 -> "$seconds s"
    seconds % 60 == 0 -> "${seconds / 60} min"
    else -> "${seconds / 60} min ${seconds % 60} s"
}

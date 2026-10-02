package app.glyphies.data

import android.util.Log
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Tiny JSON-on-disk store with atomic replace. */
class JsonFile<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    private val default: () -> T,
) {
    val exists: Boolean get() = file.exists()

    @Synchronized
    fun read(): T = try {
        if (file.exists()) AppJson.decodeFromString(serializer, file.readText()) else default()
    } catch (e: Exception) {
        Log.w("JsonFile", "Could not read ${file.name}, starting fresh", e)
        default()
    }

    @Synchronized
    fun write(value: T) {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(AppJson.encodeToString(serializer, value))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            Log.w("JsonFile", "Could not write ${file.name}", e)
        }
    }
}

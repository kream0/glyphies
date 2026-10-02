package app.glyphies.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.UUID

/** Everything made in the editor, kept in one JSON file (newest first). */
class CreationStore(context: Context, private val scope: CoroutineScope) {
    private val file = JsonFile(File(context.filesDir, "creations.json"), ListSerializer(Creation.serializer())) { emptyList() }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)

    private val _all = MutableStateFlow<List<Creation>>(emptyList())
    val all: StateFlow<List<Creation>> = _all.asStateFlow()

    init {
        val firstRun = !file.exists
        _all.value = if (firstRun) Seeds.all() else file.read()
        if (firstRun) persist()
    }

    fun get(id: String?): Creation? = id?.let { key -> _all.value.firstOrNull { it.id == key } }

    /** Adds or replaces [c] (it moves to the top of the list). */
    fun save(c: Creation) {
        val stamped = c.copy(updated = System.currentTimeMillis(), created = if (c.created == 0L) System.currentTimeMillis() else c.created)
        _all.value = listOf(stamped) + _all.value.filter { it.id != c.id }
        persist()
    }

    fun delete(id: String) {
        _all.value = _all.value.filter { it.id != id }
        persist()
    }

    fun duplicate(c: Creation, name: String): Creation {
        val copy = c.copy(id = newId(), name = name, created = 0L)
        save(copy)
        return copy
    }

    /** Puts the sample creations back (keeps yours). */
    fun restoreSamples() {
        val mine = _all.value.filter { c -> Seeds.ids.none { it == c.id } }
        _all.value = mine + Seeds.all()
        persist()
    }

    private fun persist() {
        val snapshot = _all.value
        scope.launch(io) { file.write(snapshot) }
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString().take(12)
    }
}

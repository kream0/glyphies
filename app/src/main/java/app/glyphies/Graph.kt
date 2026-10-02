package app.glyphies

import android.app.Application
import app.glyphies.data.CreationStore
import app.glyphies.data.Scores
import app.glyphies.data.Settings
import app.glyphies.play.Player
import app.glyphies.sense.Haptics
import app.glyphies.sense.Sensors
import app.glyphies.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Minimal service locator: the app is small enough not to need a DI framework. */
object Graph {
    lateinit var app: Application
        private set

    /** Process-wide scope; state bookkeeping runs on Main, IO work switches dispatchers. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val settings: Settings by lazy { Settings(app) }
    val scores: Scores by lazy { Scores(app) }
    val creations: CreationStore by lazy { CreationStore(app, scope) }
    val sensors: Sensors by lazy { Sensors(app) }
    val haptics: Haptics by lazy { Haptics(app) }
    val player: Player by lazy { Player(app, scope) }
    val updater: Updater by lazy { Updater(app, http, settings, scope) }

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun toast(message: String) {
        _messages.tryEmit(message)
    }

    fun init(application: Application) {
        app = application
    }
}

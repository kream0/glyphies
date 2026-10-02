package app.glyphies

import android.app.Application

class GlyphiesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}

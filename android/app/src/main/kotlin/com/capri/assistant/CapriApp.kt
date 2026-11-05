package com.capri.assistant

import android.app.Application
import android.content.Context
import com.capri.assistant.engine.Capri
import com.capri.assistant.system.Notify

class CapriApp : Application() {
    lateinit var capri: Capri
        private set

    override fun onCreate() {
        super.onCreate()
        Notify.channels(this)
        capri = Capri(this)
        // Plugins add their tools before her first turn; her core doesn't change for them.
        com.capri.assistant.plugins.Plugins.install(capri)
        capri.start()
    }

    companion object {
        fun capri(context: Context): Capri = (context.applicationContext as CapriApp).capri
    }
}

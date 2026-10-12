package com.creativeam.goorno

import android.app.Application
import com.creativeam.goorno.data.local.GoOrNoDatabase
import com.creativeam.goorno.data.preferences.DriverPreferencesRepository
import com.creativeam.goorno.overlay.FloatingOverlayPanel

class GoOrNoApplication : Application() {

    lateinit var database: GoOrNoDatabase
        private set

    lateinit var preferencesRepository: DriverPreferencesRepository
        private set

    lateinit var overlayPanel: FloatingOverlayPanel
        private set

    override fun onCreate() {
        super.onCreate()
        database = GoOrNoDatabase.getInstance(this)
        preferencesRepository = DriverPreferencesRepository(this)
        overlayPanel = FloatingOverlayPanel(this)
    }
}

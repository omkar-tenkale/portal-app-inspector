package io.github.portalappinspector.android

import android.app.Service
import android.content.Intent
import android.os.IBinder

class PortalAppInspectorService : Service() {
    override fun onCreate() {
        super.onCreate()
        PortalAppInspector.promoteToForeground(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PortalAppInspector.start(this)
        PortalAppInspector.promoteToForeground(this)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

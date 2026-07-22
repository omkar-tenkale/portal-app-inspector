package io.github.portalappinspector.demo

import android.app.Application
import io.github.openflocon.flocon.Flocon

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Flocon.initialize(this)
    }
}

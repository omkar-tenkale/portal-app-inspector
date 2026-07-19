package io.github.portalappinspector.android

import android.content.Context

object PortalAndroidContext {
    private var applicationContext: Context? = null

    internal fun install(context: Context) {
        applicationContext = context.applicationContext
    }

    fun requireApplicationContext(): Context =
        checkNotNull(applicationContext) {
            "Portal App Inspector has not been initialized yet."
        }
}

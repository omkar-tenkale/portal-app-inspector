package io.github.portalappinspector.android

import android.app.Activity
import android.content.Context
import java.lang.ref.WeakReference

object PortalAndroidContext {
    private var applicationContext: Context? = null
    private var currentActivity: WeakReference<Activity>? = null

    internal fun install(context: Context) {
        applicationContext = context.applicationContext
    }

    internal fun setCurrentActivity(activity: Activity) {
        currentActivity = WeakReference(activity)
    }

    fun requireApplicationContext(): Context =
        checkNotNull(applicationContext) {
            "Portal App Inspector has not been initialized yet."
        }

    fun currentActivity(): Activity? =
        currentActivity?.get()
}

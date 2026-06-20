// =====================================================================
// BlockQuestApp.kt
// Block Quest — Application class (Hilt entry point)
// =====================================================================

package com.blockquest

import android.app.Application
import android.content.pm.ApplicationInfo
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class BlockQuestApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Timber is the only logger; in release builds the
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        // plant is a no-op (see below).
        if (isDebuggable) {
            Timber.plant(Timber.DebugTree())
            Timber.d("Timber initialized in mode native DEBUG")
        } else {
            Timber.plant(ReleaseTree())
        }
    }

    /**
     * In release builds we strip every log below WARN.
     * This is the only way to keep the `Timber.d(...)` calls
     * in domain code without shipping verbose logs.
     */
    private class ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority >= android.util.Log.WARN) {
                android.util.Log.println(priority, tag ?: "BlockQuest", message)
            }
        }
    }
}

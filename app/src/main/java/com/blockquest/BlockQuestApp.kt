// =====================================================================
// BlockQuestApp.kt
// Block Quest — Application class (Hilt entry point)
// =====================================================================

package com.blockquest

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import com.blockquest.data.firebase.FirestoreSeeder
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

@HiltAndroidApp
class BlockQuestApp : Application() {
    @Inject
    lateinit var firestore: FirebaseFirestore

    /**
     * Fix for ServiceNotFoundException: No service published for: persistent_data_block
     * This service is used for Factory Reset Protection and is missing on some
     * devices/emulators. Some SDKs (like GMA or Firebase) try to access it via
     * getSystemService, which throws an exception in recent Android versions
     * because it uses getServiceOrThrow internally.
     */
    override fun getSystemService(name: String): Any? {
        if (name == Context.PERSISTENT_DATA_BLOCK_SERVICE) {
            return null
        }
        return super.getSystemService(name)
    }

    override fun onCreate() {
        super.onCreate()
        // Timber is the only logger; in release builds the
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        // plant is a no-op (see below).
        if (isDebuggable) {
            Timber.plant(Timber.DebugTree())
            Timber.d("Timber initialized in mode native DEBUG")
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            FirestoreSeeder.seedIfNeeded(firestore, scope)
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

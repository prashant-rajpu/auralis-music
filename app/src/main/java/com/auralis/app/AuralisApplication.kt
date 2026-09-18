package com.auralis.app

import android.app.Application
import com.auralis.app.data.repository.PrefsToRoomImporter
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AuralisApplication : Application() {

    @Inject
    lateinit var libraryImporter: PrefsToRoomImporter

    /**
     * Outlives any screen: the import must finish even if the user leaves immediately, or the next
     * launch would find a half-migrated library. It is a no-op after the first successful run.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { libraryImporter.runIfNeeded() }
    }
}

package com.recre.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.recre.app.core.sync.RecaudacionUploadManager
import com.recre.app.core.sync.SyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber

/**
 * Punto de entrada de la app.
 *
 * - Inicializa Hilt como contenedor de DI.
 * - Configura WorkManager con la HiltWorkerFactory para que los Workers
 *   puedan inyectar dependencias (sync de inventario, recaudaciones, etc.).
 * - Inicializa Timber en debug.
 * - Arranca [SyncManager] (T-51) y [RecaudacionUploadManager] (T-57)
 *   para que cualquier cambio de empresa activa dispare ambas tareas.
 */
@HiltAndroidApp
class RecreApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncManager: SyncManager

    @Inject
    lateinit var uploadManager: RecaudacionUploadManager

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Hilt instancia ambos managers al inyectarlos aquí; al llamar a
        // start() arrancan sus collectors del SessionState. Cualquier
        // transición a SessionState.Active(empresa) encolará una sync y
        // un intento de subir las recaudaciones pendientes.
        syncManager.start()
        uploadManager.start()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

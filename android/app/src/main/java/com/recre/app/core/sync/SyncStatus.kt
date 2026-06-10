package com.recre.app.core.sync

/**
 * Estado simplificado de la sincronización para la UI.
 *
 * No exponemos directamente [androidx.work.WorkInfo.State] porque su
 * granularidad (ENQUEUED, RUNNING, SUCCEEDED, FAILED…) no aporta valor
 * al usuario final. La UI solo necesita saber si hay trabajo en curso o
 * no.
 */
sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Running : SyncStatus
}

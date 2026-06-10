package com.recre.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * DataStore Preferences para ajustes ligeros (empresa activa, flags…).
 *
 * Para datos relacionales/persistentes seguimos usando Room; este store
 * es exclusivamente para preferencias de un único valor.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    private const val PREFS_NAME = "recre_prefs"

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = { context.preferencesDataStoreFile(PREFS_NAME) },
    )

    private fun Context.preferencesDataStoreFile(name: String) =
        java.io.File(filesDir, "datastore/$name.preferences_pb")
}

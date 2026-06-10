package com.recre.app.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provee la instancia singleton de [WorkManager] desde el contexto de la
 * aplicación. La [HiltWorkerFactory] se sigue declarando en
 * [com.recre.app.RecreApp] como `Configuration.Provider` (no entra aquí).
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkManagerModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}

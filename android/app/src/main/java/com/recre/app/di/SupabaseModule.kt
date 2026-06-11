package com.recre.app.di

import com.recre.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import javax.inject.Singleton

/**
 * Configuración del cliente de Supabase.
 *
 * URL y anon key se inyectan vía `BuildConfig` para que cada flavor pueda
 * apuntar a un proyecto distinto (dev / staging / prod). Las claves se leen
 * de `local.properties` o de variables de entorno, NUNCA hardcodeadas.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
        install(Functions)
        // Realtime reutiliza la sesión de Auth automáticamente; postgres_changes
        // aplica RLS con ese JWT, así que cada técnico solo recibe filas de su
        // empresa. Ver RealtimeManager y la migración 20260611140000.
        install(Realtime)
    }
}

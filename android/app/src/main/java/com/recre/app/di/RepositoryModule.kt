package com.recre.app.di

import com.recre.app.core.data.repository.AlertasRepository
import com.recre.app.core.data.repository.AlertasRepositoryImpl
import com.recre.app.core.data.repository.AuthRepository
import com.recre.app.core.data.repository.DeudasRepository
import com.recre.app.core.data.repository.DeudasRepositoryImpl
import com.recre.app.core.data.repository.EmpresaRepository
import com.recre.app.core.data.repository.InstalacionesGestorRepository
import com.recre.app.core.data.repository.InstalacionesGestorRepositoryImpl
import com.recre.app.core.data.repository.InventoryRepository
import com.recre.app.core.data.repository.InventoryRepositoryImpl
import com.recre.app.core.data.repository.LicenciasGestorRepository
import com.recre.app.core.data.repository.LicenciasGestorRepositoryImpl
import com.recre.app.core.data.repository.LocalesGestorRepository
import com.recre.app.core.data.repository.LocalesGestorRepositoryImpl
import com.recre.app.core.data.repository.MaquinasGestorRepository
import com.recre.app.core.data.repository.MaquinasGestorRepositoryImpl
import com.recre.app.core.data.repository.PushTokenRepository
import com.recre.app.core.data.repository.PushTokenRepositoryImpl
import com.recre.app.core.data.repository.RecaudacionHistoricaRepository
import com.recre.app.core.data.repository.RecaudacionHistoricaRepositoryImpl
import com.recre.app.core.data.repository.RecaudacionRepository
import com.recre.app.core.data.repository.RecaudacionRepositoryImpl
import com.recre.app.core.data.repository.SupabaseAuthRepository
import com.recre.app.core.data.repository.SupabaseEmpresaRepository
import com.recre.app.core.data.repository.SyncRepository
import com.recre.app.core.data.repository.SyncRepositoryImpl
import com.recre.app.core.printer.PrinterRepository
import com.recre.app.core.printer.PrinterRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: SupabaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindEmpresaRepository(impl: SupabaseEmpresaRepository): EmpresaRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds
    @Singleton
    abstract fun bindInventoryRepository(impl: InventoryRepositoryImpl): InventoryRepository

    @Binds
    @Singleton
    abstract fun bindRecaudacionRepository(impl: RecaudacionRepositoryImpl): RecaudacionRepository

    @Binds
    @Singleton
    abstract fun bindRecaudacionHistoricaRepository(
        impl: RecaudacionHistoricaRepositoryImpl,
    ): RecaudacionHistoricaRepository

    @Binds
    @Singleton
    abstract fun bindAlertasRepository(impl: AlertasRepositoryImpl): AlertasRepository

    @Binds
    @Singleton
    abstract fun bindPushTokenRepository(impl: PushTokenRepositoryImpl): PushTokenRepository

    @Binds
    @Singleton
    abstract fun bindPrinterRepository(impl: PrinterRepositoryImpl): PrinterRepository

    // CRUD gestor (T-66..T-69) ------------------------------------------------

    @Binds
    @Singleton
    abstract fun bindLicenciasGestorRepository(
        impl: LicenciasGestorRepositoryImpl,
    ): LicenciasGestorRepository

    @Binds
    @Singleton
    abstract fun bindMaquinasGestorRepository(
        impl: MaquinasGestorRepositoryImpl,
    ): MaquinasGestorRepository

    @Binds
    @Singleton
    abstract fun bindLocalesGestorRepository(
        impl: LocalesGestorRepositoryImpl,
    ): LocalesGestorRepository

    @Binds
    @Singleton
    abstract fun bindInstalacionesGestorRepository(
        impl: InstalacionesGestorRepositoryImpl,
    ): InstalacionesGestorRepository

    // Deudas: tolva y préstamos (T-215) -------------------------------------

    @Binds
    @Singleton
    abstract fun bindDeudasRepository(impl: DeudasRepositoryImpl): DeudasRepository
}

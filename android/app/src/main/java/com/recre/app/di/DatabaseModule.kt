package com.recre.app.di

import android.content.Context
import androidx.room.Room
import com.recre.app.core.data.local.RecreDatabase
import com.recre.app.core.data.local.dao.AveriaPendienteDao
import com.recre.app.core.data.local.dao.CreditoLocalDao
import com.recre.app.core.data.local.dao.CuadreRecuentoDao
import com.recre.app.core.data.local.dao.EmpresaParamsDao
import com.recre.app.core.data.local.dao.InstalacionDao
import com.recre.app.core.data.local.dao.LicenciaDao
import com.recre.app.core.data.local.dao.LocalDao
import com.recre.app.core.data.local.dao.MaquinaDao
import com.recre.app.core.data.local.dao.RecaudacionPendienteDao
import com.recre.app.core.data.local.dao.SyncMetaDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RecreDatabase =
        Room.databaseBuilder(
            context,
            RecreDatabase::class.java,
            "recre.db",
        )
            // Migraciones reales: la cola de recaudaciones contiene trabajo
            // del técnico que NO se puede perder al subir versión.
            .addMigrations(
                RecreDatabase.MIGRATION_2_3,
                RecreDatabase.MIGRATION_3_4,
                RecreDatabase.MIGRATION_4_5,
                RecreDatabase.MIGRATION_5_6,
                RecreDatabase.MIGRATION_6_7,
                RecreDatabase.MIGRATION_7_8,
                RecreDatabase.MIGRATION_8_9,
            )
            // Solo como red de seguridad para builds antiguos (alpha) que
            // hubiera v=1; los técnicos en producción nunca pasarán por ahí.
            .fallbackToDestructiveMigrationFrom(1)
            .build()

    // -------------------------------------------------------------------------
    // DAOs expuestos como providers para que cualquier feature los inyecte
    // sin tener que pasar por la database completa.
    // -------------------------------------------------------------------------

    @Provides
    fun provideEmpresaParamsDao(db: RecreDatabase): EmpresaParamsDao = db.empresaParamsDao()

    @Provides
    fun provideLocalDao(db: RecreDatabase): LocalDao = db.localDao()

    @Provides
    fun provideMaquinaDao(db: RecreDatabase): MaquinaDao = db.maquinaDao()

    @Provides
    fun provideLicenciaDao(db: RecreDatabase): LicenciaDao = db.licenciaDao()

    @Provides
    fun provideInstalacionDao(db: RecreDatabase): InstalacionDao = db.instalacionDao()

    @Provides
    fun provideSyncMetaDao(db: RecreDatabase): SyncMetaDao = db.syncMetaDao()

    @Provides
    fun provideRecaudacionPendienteDao(db: RecreDatabase): RecaudacionPendienteDao =
        db.recaudacionPendienteDao()

    @Provides
    fun provideCreditoLocalDao(db: RecreDatabase): CreditoLocalDao = db.creditoLocalDao()

    @Provides
    fun provideAveriaPendienteDao(db: RecreDatabase): AveriaPendienteDao =
        db.averiaPendienteDao()

    @Provides
    fun provideCuadreRecuentoDao(db: RecreDatabase): CuadreRecuentoDao =
        db.cuadreRecuentoDao()
}

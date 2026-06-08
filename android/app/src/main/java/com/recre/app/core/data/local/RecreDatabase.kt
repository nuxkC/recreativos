package com.recre.app.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.recre.app.core.data.local.dao.EmpresaParamsDao
import com.recre.app.core.data.local.dao.InstalacionDao
import com.recre.app.core.data.local.dao.LicenciaDao
import com.recre.app.core.data.local.dao.LocalDao
import com.recre.app.core.data.local.dao.MaquinaDao
import com.recre.app.core.data.local.dao.RecaudacionPendienteDao
import com.recre.app.core.data.local.dao.SyncMetaDao
import com.recre.app.core.data.local.entity.EmpresaParamsEntity
import com.recre.app.core.data.local.entity.InstalacionEntity
import com.recre.app.core.data.local.entity.LicenciaEntity
import com.recre.app.core.data.local.entity.LocalEntity
import com.recre.app.core.data.local.entity.MaquinaEntity
import com.recre.app.core.data.local.entity.RecaudacionPendienteEntity
import com.recre.app.core.data.local.entity.SyncMetaEntity

/**
 * Base de datos Room para cache offline + cola de recaudaciones.
 *
 * Versión 3 (T-57): añade `recaudacion_pendiente` como tabla de cola
 * offline. Migración real (no `fallbackToDestructiveMigration`) para no
 * perder recaudaciones pendientes que ya hayan tomado los técnicos.
 *
 * Cuando se añadan colas para `cambio_placa` (T-61) o
 * `lectura_no_recaudada` (futuro), seguir este mismo patrón: subir
 * versión y añadir migration.
 */
@Database(
    entities = [
        EmpresaParamsEntity::class,
        LocalEntity::class,
        MaquinaEntity::class,
        LicenciaEntity::class,
        InstalacionEntity::class,
        SyncMetaEntity::class,
        RecaudacionPendienteEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(InstantConverter::class)
abstract class RecreDatabase : RoomDatabase() {
    abstract fun empresaParamsDao(): EmpresaParamsDao
    abstract fun localDao(): LocalDao
    abstract fun maquinaDao(): MaquinaDao
    abstract fun licenciaDao(): LicenciaDao
    abstract fun instalacionDao(): InstalacionDao
    abstract fun syncMetaDao(): SyncMetaDao
    abstract fun recaudacionPendienteDao(): RecaudacionPendienteDao

    companion object {
        /**
         * v2 -> v3: añade la tabla `recaudacion_pendiente` con índices para
         * lookup por empresa, estado y deduplicación por idempotency_key.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recaudacion_pendiente` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `empresa_id` TEXT NOT NULL,
                        `instalacion_id` TEXT NOT NULL,
                        `tecnico_id` TEXT NOT NULL,
                        `fecha` INTEGER NOT NULL,
                        `contador_entradas_actual` INTEGER NOT NULL,
                        `contador_salidas_actual` INTEGER NOT NULL,
                        `baseline_entradas` INTEGER NOT NULL,
                        `baseline_salidas` INTEGER NOT NULL,
                        `baseline_origen` TEXT NOT NULL,
                        `baseline_referencia_id` TEXT,
                        `valor_credito` TEXT NOT NULL,
                        `tasa_semanal` TEXT NOT NULL,
                        `semanas` INTEGER NOT NULL,
                        `tasa_total` TEXT NOT NULL,
                        `bruto` TEXT NOT NULL,
                        `neto` TEXT NOT NULL,
                        `porcentaje_local` TEXT NOT NULL,
                        `parte_local` TEXT NOT NULL,
                        `parte_empresa` TEXT NOT NULL,
                        `desglose_total_json` TEXT NOT NULL,
                        `desglose_local_json` TEXT NOT NULL,
                        `firma_png` BLOB NOT NULL,
                        `observaciones` TEXT,
                        `dispositivo_id` TEXT,
                        `idempotency_key` TEXT NOT NULL,
                        `estado` TEXT NOT NULL,
                        `intentos` INTEGER NOT NULL,
                        `ultimo_error` TEXT,
                        `ultimo_intento_at` INTEGER,
                        `created_at` INTEGER NOT NULL,
                        `subida_at` INTEGER,
                        `recaudacion_id_remoto` TEXT,
                        `conflicto` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_recaudacion_pendiente_empresa` " +
                        "ON `recaudacion_pendiente` (`empresa_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_recaudacion_pendiente_estado` " +
                        "ON `recaudacion_pendiente` (`estado`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `idx_recaudacion_pendiente_idempotency` " +
                        "ON `recaudacion_pendiente` (`idempotency_key`)",
                )
            }
        }
    }
}

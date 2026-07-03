package com.recre.app.core.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.recre.app.core.data.local.entity.AveriaPendienteEntity
import com.recre.app.core.data.local.entity.CreditoLocalEntity
import com.recre.app.core.data.local.entity.CuadreRecuentoEntity
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
 * Versión 4 (T-211): añade `empresa_params.redondeo_recaudacion` para que
 * el cálculo en local aplique el mismo redondeo que el servidor.
 *
 * Versión 5 (T-215): tolva y préstamos. Añade el % de recuperación a
 * `empresa_params` y `local` (override), la tabla `credito_local` con las
 * deudas abiertas para el preview offline, y `recaudacion_pendiente
 * .orden_recuperacion_json` para llevar el orden manual de imputación.
 *
 * Versión 6 (T-222): añade `averia_pendiente` como cola offline de averías
 * reportadas por el técnico (mismo criterio que `recaudacion_pendiente`: el
 * reporte se persiste siempre y se sube cuando hay red).
 *
 * Versión 7 (T-225): añade `instalacion.pendiente_tolva` (merma de tolva
 * pendiente, de `v_instalacion_tolva`) para que la previa de recaudación
 * descuente la reposición antes del reparto (§5.6) y el técnico separe el
 * dinero correcto (que cuadre con lo que persiste el servidor).
 *
 * Versión 8 (T-226): añade `averia_pendiente.afecta_tolva` + `importe_tolva`
 * para que el técnico registre, al reportar la avería, el premio que la máquina
 * pagó de la tolva (§5.6). La cola lo sube a `crear_averia`, que inserta la
 * `merma` atómica; la próxima recaudación la repone antes del reparto.
 *
 * Versión 9 (cuadre): añade `cuadre_recuento`, donde el técnico persiste su
 * recuento físico de efectivo (mapa denominación->cantidad como JSON) mientras
 * prepara el cuadre semanal. Una fila por (empresa, técnico, semana); sobrevive
 * a cierres de la app sin tocar las colas de subida.
 *
 * Versión 10 (T-277): dirección estructurada del local (comunidad_autonoma,
 * provincia_codigo, municipio_codigo, calle, codigo_postal), puramente aditiva.
 *
 * Versión 11 (T-277): retira `direccion` (texto libre) de `local` vía
 * auto-migración @DeleteColumn; la dirección de display pasa a derivarse de los
 * campos estructurados. Room recrea la tabla preservando datos y FKs.
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
        CreditoLocalEntity::class,
        AveriaPendienteEntity::class,
        CuadreRecuentoEntity::class,
    ],
    version = 11,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 10, to = 11, spec = RecreDatabase.Migration10To11::class),
    ],
)
@TypeConverters(InstantConverter::class)
abstract class RecreDatabase : RoomDatabase() {
    /** v11 (T-277): retira la columna `direccion` de texto libre de `local`. */
    @DeleteColumn(tableName = "local", columnName = "direccion")
    class Migration10To11 : AutoMigrationSpec

    abstract fun empresaParamsDao(): EmpresaParamsDao
    abstract fun localDao(): LocalDao
    abstract fun maquinaDao(): MaquinaDao
    abstract fun licenciaDao(): LicenciaDao
    abstract fun instalacionDao(): InstalacionDao
    abstract fun syncMetaDao(): SyncMetaDao
    abstract fun recaudacionPendienteDao(): RecaudacionPendienteDao
    abstract fun creditoLocalDao(): CreditoLocalDao
    abstract fun averiaPendienteDao(): AveriaPendienteDao
    abstract fun cuadreRecuentoDao(): CuadreRecuentoDao

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

        /** v4 (T-211): redondeo del bruto por empresa, espejo en local. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `empresa_params` " +
                        "ADD COLUMN `redondeo_recaudacion` INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * v5 (T-215): tolva y préstamos.
         * - `empresa_params.porcentaje_recuperacion` (default 0) y override
         *   nullable `local.porcentaje_recuperacion`.
         * - tabla `credito_local` con las deudas abiertas para el preview
         *   offline de recuperación y la ficha de deudas.
         * - `recaudacion_pendiente.orden_recuperacion_json` para el orden
         *   manual de imputación que viaja al servidor.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `empresa_params` " +
                        "ADD COLUMN `porcentaje_recuperacion` INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE `local` ADD COLUMN `porcentaje_recuperacion` INTEGER",
                )
                db.execSQL(
                    "ALTER TABLE `recaudacion_pendiente` " +
                        "ADD COLUMN `orden_recuperacion_json` TEXT",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `credito_local` (
                        `credito_id` TEXT NOT NULL PRIMARY KEY,
                        `empresa_id` TEXT NOT NULL,
                        `local_id` TEXT NOT NULL,
                        `tipo` TEXT NOT NULL,
                        `instalacion_id` TEXT,
                        `principal` TEXT NOT NULL,
                        `tipo_interes` TEXT NOT NULL,
                        `fecha` TEXT NOT NULL,
                        `estado` TEXT NOT NULL,
                        `notas` TEXT,
                        `recuperado` TEXT NOT NULL,
                        `saldo` TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_credito_local_empresa` " +
                        "ON `credito_local` (`empresa_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_credito_local_local` " +
                        "ON `credito_local` (`local_id`)",
                )
            }
        }

        /**
         * v6 (T-222): cola offline de averías reportadas por el técnico.
         * Subida reanudable: `averia_id_remoto` + `recambios_subidos` permiten
         * reintentar sin duplicar la avería ni sus recambios.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `averia_pendiente` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `empresa_id` TEXT NOT NULL,
                        `maquina_id` TEXT NOT NULL,
                        `maquina_numero_serie` TEXT NOT NULL,
                        `categoria` TEXT NOT NULL,
                        `descripcion` TEXT,
                        `pone_maquina_fuera_servicio` INTEGER NOT NULL,
                        `notas` TEXT,
                        `recambios_json` TEXT NOT NULL,
                        `estado` TEXT NOT NULL,
                        `intentos` INTEGER NOT NULL,
                        `ultimo_error` TEXT,
                        `ultimo_intento_at` INTEGER,
                        `created_at` INTEGER NOT NULL,
                        `subida_at` INTEGER,
                        `averia_id_remoto` TEXT,
                        `recambios_subidos` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_averia_pendiente_empresa` " +
                        "ON `averia_pendiente` (`empresa_id`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_averia_pendiente_estado` " +
                        "ON `averia_pendiente` (`estado`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_averia_pendiente_maquina` " +
                        "ON `averia_pendiente` (`maquina_id`)",
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Merma de tolva pendiente por instalación (de v_instalacion_tolva).
                // La previa la descuenta antes del reparto (§5.6). DEFAULT '0' para
                // las filas ya sincronizadas; la próxima sync trae el valor real.
                db.execSQL(
                    "ALTER TABLE `instalacion` ADD COLUMN `pendiente_tolva` " +
                        "TEXT NOT NULL DEFAULT '0'",
                )
            }
        }

        /**
         * v8 (T-226): el reporte de avería puede registrar el premio que la
         * máquina pagó de la tolva (§5.6). `afecta_tolva` (default 0) e
         * `importe_tolva` (dinero como TEXT, nullable). La cola los sube a
         * `crear_averia`, que inserta la `merma`.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `averia_pendiente` ADD COLUMN `afecta_tolva` " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE `averia_pendiente` ADD COLUMN `importe_tolva` TEXT",
                )
            }
        }

        /**
         * v9 (cuadre): tabla `cuadre_recuento` para el recuento físico de
         * efectivo del técnico mientras prepara el cuadre semanal. PK compuesta
         * (empresa, técnico, semana); el recuento viaja como JSON. No toca las
         * colas de subida, así que es una migración puramente aditiva.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cuadre_recuento` (" +
                        "`empresa_id` TEXT NOT NULL, " +
                        "`tecnico_id` TEXT NOT NULL, " +
                        "`semana_inicio` TEXT NOT NULL, " +
                        "`recuento_json` TEXT NOT NULL, " +
                        "`updated_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`empresa_id`, `tecnico_id`, `semana_inicio`))",
                )
            }
        }

        /**
         * v10 (T-277): dirección estructurada del local. Cinco columnas text
         * nullable (espejo de las nuevas columnas de `public.local`); aditiva,
         * sin DEFAULT/NOT NULL para casar con los `String?` de LocalEntity.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `local` ADD COLUMN `comunidad_autonoma` TEXT")
                db.execSQL("ALTER TABLE `local` ADD COLUMN `provincia_codigo` TEXT")
                db.execSQL("ALTER TABLE `local` ADD COLUMN `municipio_codigo` TEXT")
                db.execSQL("ALTER TABLE `local` ADD COLUMN `calle` TEXT")
                db.execSQL("ALTER TABLE `local` ADD COLUMN `codigo_postal` TEXT")
            }
        }
    }
}

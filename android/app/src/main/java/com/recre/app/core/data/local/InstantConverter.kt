package com.recre.app.core.data.local

import androidx.room.TypeConverter
import java.time.Instant

/**
 * TypeConverter genérico para serializar [Instant] como millis epoch en SQLite.
 *
 * Centralizado aquí para que cualquier entidad que use `Instant` lo herede
 * automáticamente al registrar el converter en [RecreDatabase].
 */
class InstantConverter {

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}

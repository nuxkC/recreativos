package com.recre.app.feature.cuadre.data

import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * (De)serialización pura del recuento físico del cuadre.
 *
 * El recuento es un `Map<denominación, cantidad>` que el técnico teclea al
 * contar el efectivo. Se persiste como JSON con las claves en
 * `toPlainString()` (mismo criterio que los desgloses de
 * `RecaudacionRepository`), para no arrastrar dinero como `Double` ni perder
 * precisión en céntimos.
 *
 * Sin estado: instanciable y testeable sin Room ni Android.
 */
@Singleton
class CuadreRecuentoStore @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), Long.serializer())

    fun serializar(map: Map<BigDecimal, Long>): String =
        json.encodeToString(serializer, map.mapKeys { it.key.toPlainString() })

    fun deserializar(texto: String): Map<BigDecimal, Long> =
        runCatching {
            json.decodeFromString(serializer, texto).mapKeys { BigDecimal(it.key) }
        }.getOrDefault(emptyMap())
}

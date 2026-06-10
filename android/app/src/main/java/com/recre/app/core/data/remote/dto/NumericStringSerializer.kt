package com.recre.app.core.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

/**
 * Serializer que permite decodificar valores numéricos de JSON en Kotlin `String`.
 * PostgREST devuelve campos `numeric` como números JSON (ej. `60.00`), pero
 * la app los modela como `String` para no perder precisión antes de pasarlos a BigDecimal.
 */
object NumericStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("NumericString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String {
        return if (decoder is JsonDecoder) {
            decoder.decodeJsonElement().let { element ->
                if (element is JsonPrimitive) element.content else element.toString()
            }
        } else {
            decoder.decodeString()
        }
    }
}

package com.recre.app.core.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Frontera HTTP del registro de token FCM (T-101): invoca la Edge Function
 * `registrar-device-token` con el JWT del usuario.
 *
 * No conoce nada de Room ni de FCM: solo serializa, llama y traduce el
 * error HTTP. El `usuario_id` lo deriva el server de `auth.uid()`; aquí
 * solo enviamos empresa, token y plataforma.
 */
@Singleton
class PushTokenRemoteDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {

    suspend fun registrar(empresaId: String, token: String, plataforma: String = "android") {
        val payload = RegistrarDeviceTokenBody(
            empresaId = empresaId,
            token = token,
            plataforma = plataforma,
        )
        val response = supabase.functions.invoke("registrar-device-token", payload)
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
            val raw = runCatching { response.body<String>() }.getOrNull()
            throw RecaudacionRemoteError(
                status = response.status.value,
                code = parseErrorCode(raw),
                message = raw ?: "HTTP ${response.status.value}",
            )
        }
    }
}

@Serializable
private data class RegistrarDeviceTokenBody(
    @SerialName("empresa_id")
    val empresaId: String,
    val token: String,
    val plataforma: String,
)

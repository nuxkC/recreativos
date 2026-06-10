package com.recre.app.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.recre.app.MainActivity
import com.recre.app.R

/**
 * Construye el canal y muestra las notificaciones push (T-101).
 *
 * Centraliza:
 *  - La creación del canal Android 8+ ([asegurarCanales]), idempotente.
 *  - El armado de la notificación de resolución de conflicto con un
 *    deep-link a la pantalla de detalle, reaprovechando el enrutado de
 *    alertas in-app de T-64.
 *
 * El id del canal coincide con el `channel_id` que el backend pone en el
 * payload FCM (`_shared/push.ts` → `ANDROID_CHANNEL_CONFLICTOS`).
 */
object PushNotifier {

    /** Debe coincidir con `ANDROID_CHANNEL_CONFLICTOS` del backend. */
    const val CHANNEL_CONFLICTOS = "conflictos"

    /** Claves del payload `data` de FCM (espejo de enviar-push/mensaje.ts). */
    const val DATA_TIPO = "tipo"
    const val DATA_RECAUDACION_ID = "recaudacion_id"

    /** Extras del Intent de deep-link que lee [MainActivity]. */
    const val EXTRA_RECAUDACION_ID = "recre.extra.recaudacion_id"
    const val EXTRA_TIPO = "recre.extra.tipo"

    /**
     * Crea (idempotente) el canal de notificaciones de conflictos. Llamar
     * en el arranque de la app y antes de postear, por si el sistema lo
     * recreó.
     */
    fun asegurarCanales(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(CHANNEL_CONFLICTOS) != null) return

        val channel = NotificationChannel(
            CHANNEL_CONFLICTOS,
            context.getString(R.string.push_canal_conflictos_nombre),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.push_canal_conflictos_descripcion)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Muestra una notificación de resolución de conflicto. Al tocarla, abre
     * la app con el `recaudacion_id` para que [MainActivity] navegue al
     * detalle. Si falta el permiso POST_NOTIFICATIONS (Android 13+) la
     * publicación se ignora silenciosamente (la concede el usuario aparte).
     */
    fun mostrarConflicto(
        context: Context,
        title: String,
        body: String,
        recaudacionId: String?,
        tipo: String?,
    ) {
        asegurarCanales(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (recaudacionId != null) putExtra(EXTRA_RECAUDACION_ID, recaudacionId)
            if (tipo != null) putExtra(EXTRA_TIPO, tipo)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            recaudacionId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_CONFLICTOS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = recaudacionId?.hashCode() ?: System.currentTimeMillis().toInt()
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }
}

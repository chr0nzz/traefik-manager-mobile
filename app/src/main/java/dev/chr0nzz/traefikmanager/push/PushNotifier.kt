package dev.chr0nzz.traefikmanager.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.chr0nzz.traefikmanager.MainActivity
import dev.chr0nzz.traefikmanager.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object PushNotifier {

    private const val CHANNEL_ID = "tm_events"
    private const val CHANNEL_NAME = "Traefik Manager"

    private val json = Json { ignoreUnknownKeys = true }

    fun show(context: Context, body: ByteArray) {
        val raw = runCatching { body.toString(Charsets.UTF_8) }.getOrNull().orEmpty()
        if (raw.isBlank()) return
        val parsed = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        val message = parsed?.text("message")?.takeIf { it.isNotBlank() } ?: raw.take(400)
        val event = parsed?.text("event").orEmpty()
        val source = parsed?.text("source")?.takeIf { it.isNotBlank() } ?: "Traefik Manager"
        show(context, message, event, source)
    }

    fun show(context: Context, message: String, event: String, source: String = "Traefik Manager") {
        ensureChannel(context)
        if (!allowed(context)) return

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(source)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setColor(colourFor(event))
            .setPriority(priorityFor(event))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
        }
    }

    fun clear(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancelAll() }
    }

    fun allowed(context: Context): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Events pushed by your Traefik Manager"
            },
        )
    }

    private fun colourFor(event: String): Int = when (event.lowercase()) {
        "success" -> 0xFF22C55E.toInt()
        "warning" -> 0xFFF59E0B.toInt()
        "error" -> 0xFFEF4444.toInt()
        else -> 0xFF24A1DE.toInt()
    }

    private fun priorityFor(event: String): Int = when (event.lowercase()) {
        "error" -> NotificationCompat.PRIORITY_HIGH
        "warning" -> NotificationCompat.PRIORITY_DEFAULT
        else -> NotificationCompat.PRIORITY_LOW
    }

    private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.content.orEmpty()
}

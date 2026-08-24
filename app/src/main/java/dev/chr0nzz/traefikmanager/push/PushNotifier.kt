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

/**
 * Turns a pushed payload into a notification in the tray.
 *
 * The body is whatever the server's generic channel posts, which is
 * `{"event": severity, "message": text, "timestamp": when}`. Anything that does not parse is
 * still shown, as its own text, because a message that arrived is worth more than a clean model.
 */
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
        // The server names the category it came from, which is more use as a title than the
        // product name repeated on every line of the tray.
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

        // The id is the clock, so a burst stacks rather than each one replacing the last.
        runCatching {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
        }
    }

    /**
     * Drop everything this app has in the tray, which is also what clears the launcher's badge:
     * the dot is derived from active notifications, so reading the history in the app has to take
     * them away or it stays lit.
     */
    fun clear(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancelAll() }
    }

    /** Android 13 and up will not show anything until the user has granted this. */
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

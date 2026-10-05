package hr.podsjetnik.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object Notifier {
    private const val CHANNEL_ID = "podsjetnik_zvono"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val sound = Uri.parse("android.resource://${context.packageName}/${R.raw.zvono}")
        val channel = NotificationChannel(CHANNEL_ID, "Podsjetnici", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Obavijesti po rasporedu (npr. Trening)"
            setSound(
                sound,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, reminder: Reminder) {
        ensureChannel(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(reminder.message)
            .setContentText(time)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(reminder.id, notification)
        wakeScreen(context)
    }

    /** Kratko upali ekran (i na zaključanom mobitelu) da se obavijest vidi. */
    @Suppress("DEPRECATION")
    private fun wakeScreen(context: Context) {
        val power = context.getSystemService(PowerManager::class.java)
        if (power.isInteractive) return
        val wakeLock = power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "Podsjetnik:obavijest",
        )
        wakeLock.acquire(5_000)
    }
}

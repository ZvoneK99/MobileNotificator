package hr.podsjetnik.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId

/** Za svaki podsjetnik drži točno jedan alarm postavljen na sljedeći termin. */
object Scheduler {

    fun rescheduleAll(context: Context) {
        ReminderStore.load(context).forEach { schedule(context, it) }
    }

    fun schedule(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntent(context, reminder.id)
        alarmManager.cancel(pendingIntent)
        if (!reminder.enabled) return

        val next = reminder.nextTrigger(LocalDateTime.now()) ?: return
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (canScheduleExact(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent)
        }
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, id))
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun pendingIntent(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).putExtra(AlarmReceiver.EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

package hr.podsjetnik.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Okida se u zadano vrijeme: pokaže obavijest i postavi sljedeći termin. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val reminder = ReminderStore.load(context).find { it.id == id } ?: return
        if (reminder.enabled) Notifier.show(context, reminder)
        Scheduler.schedule(context, reminder)
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
    }
}

/** Nakon restarta mobitela, ažuriranja aplikacije ili promjene vremena alarmi se moraju ponovno postaviti. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Scheduler.rescheduleAll(context)
    }
}

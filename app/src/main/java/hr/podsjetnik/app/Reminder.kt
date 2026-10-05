package hr.podsjetnik.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/**
 * Jedan podsjetnik.
 *
 * @param days dani u tjednu, 1 = ponedjeljak ... 7 = nedjelja
 * @param startMinutes početak, u minutama od ponoći
 * @param endMinutes kraj, u minutama od ponoći; ako je kraj <= početak, kraj je sljedeći dan
 *                   (npr. 07:00 – 00:00 znači do ponoći, uključivo)
 */
data class Reminder(
    val id: Int,
    val message: String,
    val days: Set<Int>,
    val startMinutes: Int,
    val endMinutes: Int,
    val intervalMinutes: Int,
    val enabled: Boolean,
) {
    /** Sljedeći trenutak obavijesti nakon [now], ili null ako nema niti jednog. */
    fun nextTrigger(now: LocalDateTime): LocalDateTime? {
        if (days.isEmpty() || intervalMinutes <= 0) return null
        var best: LocalDateTime? = null
        // Krećemo od jučer jer jučerašnji prozor može prelaziti ponoć.
        for (offset in -1L..8L) {
            val date = now.toLocalDate().plusDays(offset)
            if (date.dayOfWeek.value !in days) continue
            val start = date.atStartOfDay().plusMinutes(startMinutes.toLong())
            var end = date.atStartOfDay().plusMinutes(endMinutes.toLong())
            if (!end.isAfter(start)) end = end.plusDays(1)
            var t = start
            while (!t.isAfter(end)) {
                if (t.isAfter(now)) {
                    if (best == null || t.isBefore(best)) best = t
                    break
                }
                t = t.plusMinutes(intervalMinutes.toLong())
            }
        }
        return best
    }

    /** Sva vremena obavijesti unutar jednog dana (u minutama od ponoći, kraj može biti 1440). */
    fun timesInWindow(): List<Int> {
        if (intervalMinutes <= 0) return emptyList()
        val end = if (endMinutes <= startMinutes) endMinutes + 24 * 60 else endMinutes
        return (startMinutes..end step intervalMinutes).toList()
    }
}

object ReminderStore {
    private const val PREFS = "podsjetnik"
    private const val KEY = "reminders"

    fun load(context: Context): List<Reminder> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY, null)
        if (json == null) {
            val defaults = listOf(
                Reminder(
                    id = 1,
                    message = "Trening",
                    days = (1..7).toSet(),
                    startMinutes = 7 * 60,
                    endMinutes = 0,
                    intervalMinutes = 60,
                    enabled = true,
                )
            )
            save(context, defaults)
            return defaults
        }
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val daysArray = o.getJSONArray("days")
            Reminder(
                id = o.getInt("id"),
                message = o.getString("message"),
                days = (0 until daysArray.length()).map { daysArray.getInt(it) }.toSet(),
                startMinutes = o.getInt("start"),
                endMinutes = o.getInt("end"),
                intervalMinutes = o.getInt("interval"),
                enabled = o.getBoolean("enabled"),
            )
        }
    }

    fun save(context: Context, reminders: List<Reminder>) {
        val array = JSONArray()
        reminders.forEach { r ->
            array.put(
                JSONObject()
                    .put("id", r.id)
                    .put("message", r.message)
                    .put("days", JSONArray(r.days.sorted()))
                    .put("start", r.startMinutes)
                    .put("end", r.endMinutes)
                    .put("interval", r.intervalMinutes)
                    .put("enabled", r.enabled)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }

    fun upsert(context: Context, reminder: Reminder) {
        val list = load(context).toMutableList()
        val index = list.indexOfFirst { it.id == reminder.id }
        if (index >= 0) list[index] = reminder else list.add(reminder)
        save(context, list)
    }

    fun delete(context: Context, id: Int) {
        save(context, load(context).filterNot { it.id == id })
    }

    fun nextId(context: Context): Int = (load(context).maxOfOrNull { it.id } ?: 0) + 1
}

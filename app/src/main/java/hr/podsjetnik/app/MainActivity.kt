package hr.podsjetnik.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val DAY_SHORT = listOf("P", "U", "S", "Č", "P", "S", "N")
private val DAY_NAMES = listOf("pon", "uto", "sri", "čet", "pet", "sub", "ned")
private val INTERVALS = listOf(15, 30, 45, 60, 90, 120, 180)

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.ensureChannel(this)
        Scheduler.rescheduleAll(this)
        setContent {
            AppTheme {
                App(resumeTick)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF7FD8B4))
        else -> lightColorScheme(primary = Color(0xFF1E6B52))
    }
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(resumeTick: Int) {
    val context = LocalContext.current
    var reminders by remember { mutableStateOf(ReminderStore.load(context), neverEqualPolicy()) }
    var editing by remember { mutableStateOf<Reminder?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    fun reload() {
        reminders = ReminderStore.load(context)
        refresh++
    }

    // Osvježi "Sljedeća: ..." kad se korisnik vrati u aplikaciju.
    LaunchedEffect(resumeTick) { reminders = ReminderStore.load(context) }

    val current = editing
    if (current != null) {
        BackHandler { editing = null }
        EditScreen(
            initial = current,
            isNew = reminders.none { it.id == current.id },
            onSave = {
                ReminderStore.upsert(context, it)
                Scheduler.schedule(context, it)
                editing = null
                reload()
            },
            onDelete = {
                Scheduler.cancel(context, current.id)
                ReminderStore.delete(context, current.id)
                editing = null
                reload()
            },
            onCancel = { editing = null },
        )
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Podsjetnik") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editing = Reminder(
                        id = ReminderStore.nextId(context),
                        message = "",
                        days = (1..7).toSet(),
                        startMinutes = 8 * 60,
                        endMinutes = 20 * 60,
                        intervalMinutes = 60,
                        enabled = true,
                    )
                },
            ) { Text("+  Novi podsjetnik") }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "setup") { SetupCard(resumeTick + refresh) }
            if (reminders.isEmpty()) {
                item {
                    Text(
                        "Nema podsjetnika. Dodaj novi gumbom dolje.",
                        Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            items(reminders, key = { it.id }) { reminder ->
                ReminderCard(
                    reminder = reminder,
                    onClick = { editing = reminder },
                    onToggle = { enabled ->
                        val updated = reminder.copy(enabled = enabled)
                        ReminderStore.upsert(context, updated)
                        Scheduler.schedule(context, updated)
                        reload()
                    },
                )
            }
        }
    }
}

@Composable
private fun ReminderCard(reminder: Reminder, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    reminder.message.ifBlank { "(bez teksta)" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(daysSummary(reminder.days), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${formatMinutes(reminder.startMinutes)} – ${formatMinutes(reminder.endMinutes)}" +
                        " · svakih ${formatInterval(reminder.intervalMinutes)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val next = if (reminder.enabled) reminder.nextTrigger(LocalDateTime.now()) else null
                Text(
                    when {
                        !reminder.enabled -> "Isključeno"
                        next == null -> "Nema termina"
                        else -> "Sljedeća: ${formatNext(next)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Switch(checked = reminder.enabled, onCheckedChange = onToggle)
        }
    }
}

/** Provjere koje na Honor/Huawei mobitelima odlučuju hoće li obavijesti stvarno stizati. */
@SuppressLint("BatteryLife")
@Composable
private fun SetupCard(tick: Int) {
    val context = LocalContext.current
    var permissionTick by remember { mutableIntStateOf(0) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionTick++
    }
    // tick/permissionTick se čitaju da se stanje osvježi nakon povratka iz postavki
    val key = tick + permissionTick
    val notificationsOk = remember(key) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val exactOk = remember(key) { Scheduler.canScheduleExact(context) }
    val batteryOk = remember(key) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    var expanded by remember { mutableStateOf(false) }
    val allOk = notificationsOk && exactOk && batteryOk

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (allOk) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (allOk) "Postavke su u redu" else "Potrebne postavke",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (allOk) {
                    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Sakrij" else "Detalji") }
                }
            }
            if (!allOk || expanded) {
                SetupRow("Obavijesti dopuštene", notificationsOk) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    }
                }
                SetupRow("Točni alarmi", exactOk) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                        )
                    }
                }
                SetupRow("Bez ograničenja baterije", batteryOk) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"),
                        )
                    )
                }
                Text(
                    "Honor: otvori Postavke → Baterija → Pokretanje aplikacija → Podsjetnik, " +
                        "isključi \"Upravljaj automatski\" i uključi sve tri opcije " +
                        "(automatsko pokretanje, sekundarno pokretanje, rad u pozadini).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openAppSettings(context) }) { Text("Postavke aplikacije") }
                    OutlinedButton(onClick = {
                        Notifier.show(context, Reminder(9999, "Test obavijest", emptySet(), 0, 0, 60, true))
                    }) { Text("Test") }
                }
            }
        }
    }
}

@Composable
private fun SetupRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text((if (ok) "✓  " else "✗  ") + label, Modifier.weight(1f))
        if (!ok) Button(onClick = onFix) { Text("Dozvoli") }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditScreen(
    initial: Reminder,
    isNew: Boolean,
    onSave: (Reminder) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var message by remember { mutableStateOf(initial.message) }
    var days by remember { mutableStateOf(initial.days) }
    var start by remember { mutableIntStateOf(initial.startMinutes) }
    var end by remember { mutableIntStateOf(initial.endMinutes) }
    var interval by remember { mutableIntStateOf(initial.intervalMinutes) }
    val draft = initial.copy(
        message = message.trim(),
        days = days,
        startMinutes = start,
        endMinutes = end,
        intervalMinutes = interval,
        enabled = if (isNew) true else initial.enabled,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Novi podsjetnik" else "Uredi podsjetnik") },
                navigationIcon = { TextButton(onClick = onCancel) { Text("Natrag") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Tekst obavijesti") },
                placeholder = { Text("npr. Trening") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Dani", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { day ->
                    val selected = day in days
                    Surface(
                        shape = CircleShape,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clickable { days = if (selected) days - day else days + day },
                    ) {
                        Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(DAY_SHORT[day - 1], fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { days = (1..7).toSet() }) { Text("Svaki dan") }
                TextButton(onClick = { days = (1..5).toSet() }) { Text("Radni dani") }
                TextButton(onClick = { days = setOf(6, 7) }) { Text("Vikend") }
            }

            Text("Vrijeme", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField("Od", start, Modifier.weight(1f)) { pickTime(context, start) { start = it } }
                TimeField("Do", end, Modifier.weight(1f)) { pickTime(context, end) { end = it } }
            }
            if (end <= start) {
                Text(
                    "Kraj je nakon ponoći (sljedeći dan), npr. 00:00 = do ponoći uključivo.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text("Svakih", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                INTERVALS.forEach { minutes ->
                    FilterChip(
                        selected = interval == minutes,
                        onClick = { interval = minutes },
                        label = { Text(formatInterval(minutes)) },
                    )
                }
            }

            val times = draft.timesInWindow()
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${times.size} obavijesti dnevno", fontWeight = FontWeight.SemiBold)
                    Text(
                        times.joinToString(", ") { formatMinutes(it) },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onSave(draft) },
                enabled = message.isNotBlank() && days.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Spremi") }
            OutlinedButton(
                onClick = { Notifier.show(context, draft.copy(message = draft.message.ifBlank { "Test" })) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Isprobaj obavijest") }
            if (!isNew) {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Obriši podsjetnik", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun TimeField(label: String, minutes: Int, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(formatMinutes(minutes), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

private fun pickTime(context: Context, current: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(hour * 60 + minute) },
        current / 60,
        current % 60,
        true,
    ).show()
}

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    )
}

private fun formatMinutes(minutes: Int): String {
    val m = minutes % (24 * 60)
    return "%02d:%02d".format(m / 60, m % 60)
}

private fun formatInterval(minutes: Int): String = when {
    minutes % 60 == 0 && minutes >= 60 -> if (minutes == 60) "1 h" else "${minutes / 60} h"
    minutes > 60 -> "${minutes / 60} h ${minutes % 60} min"
    else -> "$minutes min"
}

private fun daysSummary(days: Set<Int>): String = when (days) {
    (1..7).toSet() -> "Svaki dan"
    (1..5).toSet() -> "Radni dani"
    setOf(6, 7) -> "Vikend"
    emptySet<Int>() -> "Nijedan dan"
    else -> days.sorted().joinToString(", ") { DAY_NAMES[it - 1] }
}

private fun formatNext(next: LocalDateTime): String {
    val today = LocalDate.now()
    val time = next.format(DateTimeFormatter.ofPattern("HH:mm"))
    return when (next.toLocalDate()) {
        today -> "danas u $time"
        today.plusDays(1) -> "sutra u $time"
        else -> "${DAY_NAMES[next.dayOfWeek.value - 1]} u $time"
    }
}

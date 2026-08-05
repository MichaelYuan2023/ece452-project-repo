package com.example.houseflow.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.houseflow.model.BlockType
import com.example.houseflow.model.BusyBlock
import com.example.houseflow.model.Recurrence
import com.example.houseflow.ui.components.EmptyState
import com.example.houseflow.ui.components.HFCard
import com.example.houseflow.ui.components.Pill
import com.example.houseflow.ui.viewmodel.AppViewModel
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID
import androidx.compose.material.icons.filled.EventBusy

private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val WEEKDAY_INITIALS = listOf("M", "T", "W", "T", "F", "S", "S")
private val HOURS = (0..23).map { h -> "%02d:00".format(h) }
private val BLOCK_TYPES = BlockType.entries.map { it.name }
private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailabilityScreen(vm: AppViewModel, onBack: (() -> Unit)? = null) {
    val blocks by vm.myBusyBlocks.collectAsState()
    val currentUser by vm.currentUser.collectAsState()

    var showDialog by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    // First-of-month, local midnight, for the visible month.
    var visibleMonthStart by remember { mutableLongStateOf(firstOfMonthMillis(todayMidnight())) }
    var selectedDate by remember { mutableLongStateOf(todayMidnight()) }

    val dayBlocks = blocksForDate(blocks, selectedDate)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Schedule") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showImport = true }) {
                        Icon(Icons.Default.Upload, contentDescription = "Import calendar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add busy block")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                MonthCalendarCard(
                    visibleMonthStart = visibleMonthStart,
                    selectedDate = selectedDate,
                    blocks = blocks,
                    onPrevMonth = { visibleMonthStart = shiftMonth(visibleMonthStart, -1) },
                    onNextMonth = { visibleMonthStart = shiftMonth(visibleMonthStart, 1) },
                    onSelectDate = { selectedDate = it }
                )
            }

            item {
                Text(
                    dayHeading(selectedDate),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (dayBlocks.isEmpty()) {
                item {
                    HFCard { EmptyState(Icons.Default.EventBusy, "No busy blocks", "Tap + to add one for this day.") }
                }
            } else {
                items(dayBlocks, key = { it.id }) { block ->
                    BusyBlockRow(block) { vm.deleteBusyBlock(block.id) }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }

        if (showDialog) {
            AddBusyBlockDialog(
                roommateId = currentUser?.uid ?: "",
                selectedDate = selectedDate,
                onDismiss = { showDialog = false },
                onConfirm = { block ->
                    vm.addBusyBlock(block)
                    showDialog = false
                }
            )
        }

        if (showImport) {
            ImportCalendarDialog(vm = vm, onDismiss = { showImport = false })
        }
    }
}

@Composable
private fun ImportCalendarDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    fun handleResult(result: Result<Int>) {
        busy = false
        result
            .onSuccess { count -> isError = false; message = "Imported $count event${if (count == 1) "" else "s"}." }
            .onFailure { isError = true; message = it.message ?: "Import failed." }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true; message = null
        scope.launch {
            val result = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Couldn't read the file")
            }.fold(
                onSuccess = { text -> vm.importCalendarFromText(text) },
                onFailure = { Result.failure(it) }
            )
            handleResult(result)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Import calendar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Import your class schedule from an .ics calendar. Re-importing updates it without duplicates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Calendar URL (.ics / webcal)") },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    enabled = !busy && url.isNotBlank(),
                    onClick = {
                        busy = true; message = null
                        scope.launch { handleResult(vm.importCalendarFromUrl(url)) }
                    }
                ) { Text("Import from URL") }

                TextButton(
                    enabled = !busy,
                    onClick = { filePicker.launch(arrayOf("text/calendar", "text/plain", "application/octet-stream", "*/*")) }
                ) { Text("Import from file") }

                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Spacer(Modifier.height(0.dp))
                        Text("  Importing…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Done") }
        }
    )
}

@Composable
private fun MonthCalendarCard(
    visibleMonthStart: Long,
    selectedDate: Long,
    blocks: List<BusyBlock>,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (Long) -> Unit
) {
    HFCard(contentPadding = 12.dp) {
        // Month header with navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPrevMonth) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
            }
            Text(monthTitle(visibleMonthStart), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onNextMonth) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
            }
        }

        // Weekday header
        Row(Modifier.fillMaxWidth()) {
            WEEKDAY_INITIALS.forEach { initial ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        initial,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        val cells = monthCells(visibleMonthStart)
        val today = todayMidnight()
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { dayMillis ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (dayMillis == null) {
                            Spacer(Modifier.size(40.dp))
                        } else {
                            DayCell(
                                dayMillis = dayMillis,
                                isSelected = sameLocalDay(dayMillis, selectedDate),
                                isToday = sameLocalDay(dayMillis, today),
                                hasEvents = blocksForDate(blocks, dayMillis).isNotEmpty(),
                                onClick = { onSelectDate(dayMillis) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    dayMillis: Long,
    isSelected: Boolean,
    isToday: Boolean,
    hasEvents: Boolean,
    onClick: () -> Unit
) {
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primary
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val fg = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .padding(2.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(bg)
            .then(
                if (isToday && !isSelected)
                    Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                dayOfMonth(dayMillis).toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = fg,
                fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal
            )
            if (hasEvents) {
                Box(
                    Modifier
                        .padding(top = 1.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
private fun BusyBlockRow(block: BusyBlock, onDelete: () -> Unit) {
    HFCard(contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(block.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${"%02d:00".format(block.startHour)} – ${"%02d:00".format(block.endHour)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(block.type.name.lowercase().replaceFirstChar { it.uppercase() })
                    if (block.recurrence == Recurrence.WEEKLY) {
                        Pill("Weekly", tint = MaterialTheme.colorScheme.tertiary)
                    }
                    if (block.sourceUid != null) {
                        Pill("Imported", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBusyBlockDialog(
    roommateId: String,
    selectedDate: Long,
    onDismiss: () -> Unit,
    onConfirm: (BusyBlock) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedStart by remember { mutableIntStateOf(9) }
    var selectedEnd by remember { mutableIntStateOf(11) }
    var selectedType by remember { mutableStateOf(BlockType.CLASS) }
    var repeatsWeekly by remember { mutableStateOf(false) }

    val dow = mondayIndex(selectedDate)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Busy Block") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    dayHeading(selectedDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (e.g. Math class)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        SimpleDropdown("Start", HOURS, selectedStart) { selectedStart = it }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        SimpleDropdown("End", HOURS, selectedEnd) { selectedEnd = it }
                    }
                }
                SimpleDropdown("Type", BLOCK_TYPES, selectedType.ordinal) {
                    selectedType = BlockType.entries[it]
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Repeats every week", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (repeatsWeekly) "Every ${DAYS[dow]}" else "Only on ${dayHeading(selectedDate)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = repeatsWeekly, onCheckedChange = { repeatsWeekly = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && selectedEnd > selectedStart,
                onClick = {
                    onConfirm(
                        BusyBlock(
                            id = UUID.randomUUID().toString(),
                            roommateId = roommateId,
                            dayOfWeek = dow,
                            startHour = selectedStart,
                            endHour = selectedEnd,
                            title = title.trim(),
                            type = selectedType,
                            recurrence = if (repeatsWeekly) Recurrence.WEEKLY else Recurrence.ONE_TIME,
                            date = if (repeatsWeekly) null else selectedDate
                        )
                    )
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// The shared dropdown used across dialogs in several screens.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleDropdown(label: String, options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = options.getOrElse(selectedIndex) { "" },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { idx, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(idx)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ---- Date helpers (local time) ----

private const val DAY_MS_UI: Long = 24L * 3600 * 1000

private fun todayMidnight(): Long = atMidnight(Calendar.getInstance())

private fun atMidnight(cal: Calendar): Long {
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun firstOfMonthMillis(anyDayInMonth: Long): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = anyDayInMonth }
    cal.set(Calendar.DAY_OF_MONTH, 1)
    return atMidnight(cal)
}

private fun shiftMonth(monthStart: Long, delta: Int): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = monthStart }
    cal.add(Calendar.MONTH, delta)
    return firstOfMonthMillis(cal.timeInMillis)
}

private fun monthTitle(monthStart: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = monthStart }
    return "${MONTHS[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)}"
}

private fun dayOfMonth(dateMillis: Long): Int =
    Calendar.getInstance().apply { timeInMillis = dateMillis }.get(Calendar.DAY_OF_MONTH)

// 0=Monday … 6=Sunday
private fun mondayIndex(dateMillis: Long): Int {
    val dow = Calendar.getInstance().apply { timeInMillis = dateMillis }.get(Calendar.DAY_OF_WEEK)
    return (dow + 5) % 7
}

private fun dayHeading(dateMillis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
    return "${DAYS[mondayIndex(dateMillis)]}, ${MONTHS[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.DAY_OF_MONTH)}"
}

// Cells for the visible month, Monday-first, with null padding for blanks.
private fun monthCells(monthStart: Long): List<Long?> {
    val cal = Calendar.getInstance().apply { timeInMillis = monthStart }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val lead = mondayIndex(monthStart)
    val cells = ArrayList<Long?>()
    repeat(lead) { cells.add(null) }
    for (day in 1..daysInMonth) {
        val c = Calendar.getInstance().apply { timeInMillis = monthStart }
        c.set(Calendar.DAY_OF_MONTH, day)
        cells.add(atMidnight(c))
    }
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}

private fun sameLocalDay(a: Long, b: Long): Boolean {
    val tz = TimeZone.getDefault()
    return (a + tz.getOffset(a)) / DAY_MS_UI == (b + tz.getOffset(b)) / DAY_MS_UI
}

// Blocks that apply on [dateMillis]: weekly ones on the matching weekday plus
// one-time events on that exact date.
private fun blocksForDate(blocks: List<BusyBlock>, dateMillis: Long): List<BusyBlock> {
    val dow = mondayIndex(dateMillis)
    return blocks
        .filter { b ->
            when (b.recurrence) {
                Recurrence.WEEKLY -> b.dayOfWeek == dow
                Recurrence.ONE_TIME -> b.date != null && sameLocalDay(b.date, dateMillis)
            }
        }
        .sortedBy { it.startHour }
}

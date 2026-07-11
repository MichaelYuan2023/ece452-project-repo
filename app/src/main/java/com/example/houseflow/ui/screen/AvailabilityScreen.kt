package com.example.houseflow.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.houseflow.model.BlockType
import com.example.houseflow.model.BusyBlock
import com.example.houseflow.ui.viewmodel.AppViewModel
import java.util.UUID

private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val HOURS = (0..23).map { h -> "%02d:00".format(h) }
private val BLOCK_TYPES = BlockType.entries.map { it.name }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailabilityScreen(vm: AppViewModel, onSignOut: () -> Unit = {}) {
    val blocks by vm.myBusyBlocks.collectAsState()
    val currentUser by vm.currentUser.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Schedule") },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Sign out"
                        )
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
        if (blocks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No busy blocks yet. Tap + to add one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                items(blocks, key = { it.id }) { block ->
                    BusyBlockRow(block) { vm.deleteBusyBlock(block.id) }
                }
            }
        }

        if (showDialog) {
            AddBusyBlockDialog(
                roommateId = currentUser?.uid ?: "",
                onDismiss = { showDialog = false },
                onConfirm = { block ->
                    vm.addBusyBlock(block)
                    showDialog = false
                }
            )
        }
    }
}

@Composable
private fun BusyBlockRow(block: BusyBlock, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(block.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${DAYS[block.dayOfWeek]}  ${"%02d:00".format(block.startHour)} – ${"%02d:00".format(block.endHour)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(block.type.name, style = MaterialTheme.typography.labelSmall)
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
    onDismiss: () -> Unit,
    onConfirm: (BusyBlock) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedDay by remember { mutableIntStateOf(0) }
    var selectedStart by remember { mutableIntStateOf(9) }
    var selectedEnd by remember { mutableIntStateOf(11) }
    var selectedType by remember { mutableStateOf(BlockType.CLASS) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Busy Block") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (e.g. Math class)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SimpleDropdown("Day", DAYS, selectedDay) { selectedDay = it }
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank() && selectedEnd > selectedStart) {
                        onConfirm(
                            BusyBlock(
                                id = UUID.randomUUID().toString(),
                                roommateId = roommateId,
                                dayOfWeek = selectedDay,
                                startHour = selectedStart,
                                endHour = selectedEnd,
                                title = title.trim(),
                                type = selectedType
                            )
                        )
                    }
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

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

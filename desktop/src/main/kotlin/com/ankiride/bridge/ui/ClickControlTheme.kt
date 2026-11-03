package com.ankiride.bridge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ankiride.bridge.core.*

val ClickControlColors = darkColorScheme(
    primary = Color(0xFF70D8C8), onPrimary = Color(0xFF062B28),
    primaryContainer = Color(0xFF24564F), onPrimaryContainer = Color(0xFFCEFFF5),
    background = Color(0xFF101B22), onBackground = Color(0xFFECF5F4),
    surface = Color(0xFF182A33), onSurface = Color(0xFFECF5F4), surfaceTint = Color(0xFF70D8C8),
    surfaceContainerLowest = Color(0xFF0C171D), surfaceContainerLow = Color(0xFF14242B),
    surfaceContainer = Color(0xFF182A33), surfaceContainerHigh = Color(0xFF213943), surfaceContainerHighest = Color(0xFF2B454D),
    inverseSurface = Color(0xFFD7E8E5), inverseOnSurface = Color(0xFF203331), inversePrimary = Color(0xFF216D62),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    surfaceVariant = Color(0xFF213943), onSurfaceVariant = Color(0xFFB5C9C8),
    outline = Color(0xFF829B9A), secondary = Color(0xFFB5C9C8),
    onSecondary = Color(0xFF173230), secondaryContainer = Color(0xFF344C4A), onSecondaryContainer = Color(0xFFD0E6E2)
)

@Composable
fun PresetEditor(config: AppConfiguration, onChange: (AppConfiguration) -> Unit, allowAnkiDroid: Boolean, onDirtyChanged: (Boolean) -> Unit = {}) {
    val current = PresetStore.active(config) ?: return
    var draft by remember(current.id, current) { mutableStateOf(current) }
    var selectionOpen by remember { mutableStateOf(false) }
    var actionMenu by remember { mutableStateOf<PresetBindingKey?>(null) }
    var pendingSelection by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dirty = draft != current
    LaunchedEffect(dirty) { onDirtyChanged(dirty) }
    var managementOpen by remember { mutableStateOf(false) }
    var rightPod by remember { mutableStateOf(true) }
    var selectedButton by remember { mutableStateOf(ClickButton.Y) }
    var buttonMenu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Presets", style = MaterialTheme.typography.headlineSmall)
        Box {
            OutlinedButton(onClick = { selectionOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Active: ${current.name} ▾") }
            DropdownMenu(expanded = selectionOpen, onDismissRequest = { selectionOpen = false }) {
                config.presets.forEach { preset -> DropdownMenuItem(text = { Text(preset.name) }, onClick = {
                    selectionOpen = false
                    if (dirty) pendingSelection = preset.id else onChange(config.copy(activePresetId = preset.id))
                }) }
            }
        }
        OutlinedTextField(value = draft.name, onValueChange = { draft = draft.copy(name = it) }, singleLine = true, label = { Text("Preset name") }, modifier = Modifier.fillMaxWidth())
        if (allowAnkiDroid) {
            Text("Anki destination", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(AnkiTarget.ANKIDROID to "AnkiDroid", AnkiTarget.ANKICONNECT to "Laptop Anki").forEach { (target, label) ->
                    FilterChip(selected = draft.ankiTarget == target, onClick = { draft = draft.copy(ankiTarget = target) }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
        } else Text("Destination: local Anki", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (dirty) "Unsaved changes" else "Saved", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (draft.name.isNotBlank()) onChange(config.copy(presets = config.presets.map { if (it.id == current.id) draft.copy(name = draft.name.trim()) else it })) }, enabled = dirty && draft.name.isNotBlank(), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Save") }
            Box {
                OutlinedButton(onClick = { managementOpen = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Manage ▾") }
                DropdownMenu(expanded = managementOpen, onDismissRequest = { managementOpen = false }) {
                    DropdownMenuItem(text = { Text("Create blank") }, enabled = !dirty, onClick = {
                        val preset = PresetStore.snapshot("New preset", if (allowAnkiDroid) AnkiTarget.ANKIDROID else AnkiTarget.ANKICONNECT) { _, _ -> MappedTargetAction.NONE }
                        onChange(config.copy(presets = config.presets + preset, activePresetId = preset.id)); managementOpen = false
                    })
                    DropdownMenuItem(text = { Text("Create from standard") }, enabled = !dirty, onClick = {
                        val preset = MappingPreset(PresetStore.newId(), "Standard controls", if (allowAnkiDroid) AnkiTarget.ANKIDROID else AnkiTarget.ANKICONNECT, PresetStore.standardBindings())
                        onChange(config.copy(presets = config.presets + preset, activePresetId = preset.id)); managementOpen = false
                    })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = {
                        val duplicate = draft.copy(id = PresetStore.newId(), name = "${draft.name} copy")
                        onChange(config.copy(presets = config.presets + duplicate, activePresetId = duplicate.id)); managementOpen = false
                    })
                    DropdownMenuItem(text = { Text("Reset to standard") }, onClick = { draft = draft.copy(bindings = PresetStore.standardBindings()); managementOpen = false })
                    DropdownMenuItem(text = { Text("Clear all actions") }, onClick = { draft = draft.copy(bindings = current.bindings.mapValues { MappedTargetAction.NONE }); managementOpen = false })
                    DropdownMenuItem(text = { Text("Discard changes") }, enabled = dirty, onClick = { draft = current; managementOpen = false })
                    DropdownMenuItem(text = { Text("Delete preset") }, enabled = config.presets.size > 1, onClick = { confirmDelete = true; managementOpen = false })
                }
            }
        }
        HorizontalDivider()
        Text("Button actions", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(true to "Right controller", false to "Left controller").forEach { (right, label) ->
                FilterChip(selected = rightPod == right, onClick = { rightPod = right; selectedButton = if (right) ClickButton.Y else ClickButton.SHIFT_DOWN_LEFT }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        val buttons = ClickButton.entries.map { it.canonicalButton }.distinct().filter { it.isRightPod == rightPod && (it.isPrimaryConfigurable || it == ClickButton.SHIFT_UP_LEFT || it == ClickButton.SHIFT_DOWN_RIGHT) }
        Box {
            OutlinedButton(onClick = { buttonMenu = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("${selectedButton.displayName} ▾") }
            DropdownMenu(expanded = buttonMenu, onDismissRequest = { buttonMenu = false }) {
                buttons.forEach { button -> DropdownMenuItem(text = { Text(button.displayName) }, onClick = { selectedButton = button; buttonMenu = false }) }
            }
        }
        PressType.entries.forEach { press ->
            val key = PresetBindingKey(selectedButton, press)
            Box {
                OutlinedButton(onClick = { actionMenu = key }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("${if (press == PressType.SINGLE_CLICK) "Press" else "Hold"}: ${(draft.bindings[key] ?: MappedTargetAction.NONE).label}")
                }
                DropdownMenu(expanded = actionMenu == key, onDismissRequest = { actionMenu = null }, modifier = Modifier.heightIn(max = 400.dp)) {
                    MappedTargetAction.entries.forEach { action -> DropdownMenuItem(text = { Text(action.label) }, onClick = {
                        draft = draft.copy(bindings = draft.bindings + (key to action)); actionMenu = null
                    }) }
                }
            }
        }
    }
    if (pendingSelection != null) AlertDialog(onDismissRequest = { pendingSelection = null }, title = { Text("Discard unsaved changes?") }, text = { Text("Your changes to ${current.name} have not been saved.") }, confirmButton = { TextButton(onClick = { onChange(config.copy(activePresetId = pendingSelection)); pendingSelection = null }) { Text("Discard") } }, dismissButton = { TextButton(onClick = { pendingSelection = null }) { Text("Keep editing") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete ${current.name}?") }, text = { Text("This removes the saved preset.") }, confirmButton = { TextButton(onClick = { val remaining = config.presets.filterNot { it.id == current.id }; onChange(config.copy(presets = remaining, activePresetId = remaining.first().id)); confirmDelete = false }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}

@Composable
fun MappingOverview(config: AppConfiguration) {
    val preset = PresetStore.active(config) ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Anki: ${if (preset.ankiTarget == AnkiTarget.ANKIDROID) "AnkiDroid" else "AnkiConnect"}", style = MaterialTheme.typography.titleMedium)
        ClickButton.entries.map { it.canonicalButton }.distinct().filter { it.isPrimaryConfigurable || it == ClickButton.SHIFT_UP_LEFT || it == ClickButton.SHIFT_DOWN_RIGHT }.forEach { button ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text(button.displayName, style = MaterialTheme.typography.titleSmall)
                Text("Press: ${(preset.bindings[PresetBindingKey(button, PressType.SINGLE_CLICK)] ?: MappedTargetAction.NONE).label}")
                Text("Hold: ${(preset.bindings[PresetBindingKey(button, PressType.LONG_PRESS)] ?: MappedTargetAction.NONE).label}")
            } }
        }
    }
}

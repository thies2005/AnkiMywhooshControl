package com.ankiride.bridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankiride.bridge.core.*
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Main Compose Desktop application UI for AnkiRide Bridge (Ubuntu / macOS / Windows).
 * Provides a clean dark-themed Material3 dashboard featuring:
 * 1. Top Status & Control Card (Bridge State, BLE State, Pod MACs, Start/Stop)
 * 2. Interactive Virtual Zwift Click v2 Simulator Card (Single/Long Press for all 10 buttons)
 * 3. Operating Mode Selector Card (Modes 1 to 5)
 * 4. AnkiConnect (:8765) & OpenBikeControl (:21587) Card (Host, Port, Test actions)
 * 5. Desktop Options Card (Single-pod mode, Z-shift, HUD pill, Audio cues, Robot fallback)
 * 6. Color-Coded Custom Button Mapping Editor (Mode tabs, Presets, Reset)
 * 7. Live Diagnostic Log Viewer (Filters, Monospace viewer, Copy to clipboard, Clear)
 */
@Composable
fun DesktopAppUi(
    config: AppConfiguration,
    onConfigChange: (AppConfiguration) -> Unit,
    isBridgeRunning: Boolean,
    onToggleBridge: () -> Unit,
    bleState: BleConnectionState,
    connectedRightMac: String?,
    connectedLeftMac: String?,
    hasMyWhooshClient: Boolean,
    onSimulateGesture: (ClickButton, PressType) -> Unit,
    onTestAnkiAction: (AnkiAction) -> Unit,
    onTestMyWhooshAction: (MyWhooshAction) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AnkiRide Bridge",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Zwift Click v2 Desktop Bridge for Anki & MyWhoosh (Ubuntu / macOS / Windows)",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. Top Status & Control Card
        TopStatusCard(
            isBridgeRunning = isBridgeRunning,
            bleState = bleState,
            connectedRightMac = connectedRightMac,
            connectedLeftMac = connectedLeftMac,
            hasMyWhooshClient = hasMyWhooshClient,
            openBikePort = config.openBikeServerPort,
            onToggleBridge = onToggleBridge
        )

        // 2. Interactive Virtual Zwift Click v2 Simulator Card
        VirtualZwiftClickSimulatorCard(
            mode = config.mode,
            customBindings = config.customBindings,
            onSimulateGesture = onSimulateGesture
        )

        // 3. Operating Mode Selector Card
        ModeSelectorCard(
            selectedMode = config.mode,
            onSelectMode = { newMode ->
                onConfigChange(config.copy(mode = newMode))
            }
        )

        // 4. AnkiConnect (:8765) & OpenBikeControl (:21587) Card
        NetworkingTestCard(
            config = config,
            onConfigChange = onConfigChange,
            hasMyWhooshClient = hasMyWhooshClient,
            onTestAnkiAction = onTestAnkiAction,
            onTestMyWhooshAction = onTestMyWhooshAction
        )

        // 5. Desktop Options Card
        DesktopOptionsCard(
            config = config,
            onConfigChange = onConfigChange
        )

        // 6. Color-Coded Custom Button Mapping Editor
        CustomButtonMappingEditorCard(
            activeMode = config.mode,
            rightPodOnly = config.rightPodOnlyNoZwift,
            customBindings = config.customBindings,
            onUpdateBindings = { updated ->
                onConfigChange(config.copy(customBindings = updated))
            }
        )

        // 7. Live Diagnostic Log Viewer
        DiagnosticLogViewerCard()
    }
}

// ---------------------------------------------------------------------------
// 1. Top Status & Control Card
// ---------------------------------------------------------------------------

@Composable
fun TopStatusCard(
    isBridgeRunning: Boolean,
    bleState: BleConnectionState,
    connectedRightMac: String?,
    connectedLeftMac: String?,
    hasMyWhooshClient: Boolean,
    openBikePort: Int,
    onToggleBridge: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isBridgeRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isBridgeRunning) "OPENBIKECONTROL SERVER & BLE BRIDGE ACTIVE" else "BRIDGE STOPPED",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = if (isBridgeRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isBridgeRunning) {
                            "Listening on TCP port $openBikePort • mDNS _openbikecontrol._tcp.local. • BLE scanning/connected"
                        } else {
                            "Click 'Start Bridge' to connect Zwift Click v2 & advertise OpenBikeControl to MyWhoosh"
                        },
                        fontSize = 12.sp,
                        color = if (isBridgeRunning) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onToggleBridge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBridgeRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isBridgeRunning) "Stop Bridge" else "Start Bridge", fontWeight = FontWeight.Bold)
                }
            }

            // Status badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // BLE Connection Status Badge
                val bleBadgeText = when (bleState) {
                    BleConnectionState.DISCONNECTED -> "BLE: Disconnected"
                    BleConnectionState.SCANNING -> "BLE: Scanning for Zwift Click..."
                    BleConnectionState.CONNECTING -> "BLE: Connecting..."
                    BleConnectionState.HANDSHAKING -> "BLE: Sending RideOn Handshake..."
                    BleConnectionState.CONNECTED_RIGHT_ONLY -> "BLE: Connected (Right Pod)"
                    BleConnectionState.CONNECTED_BOTH_PODS -> "BLE: Connected (Both Pods)"
                    BleConnectionState.ERROR -> "BLE: Connection Error"
                }
                val bleBadgeColor = when (bleState) {
                    BleConnectionState.CONNECTED_RIGHT_ONLY,
                    BleConnectionState.CONNECTED_BOTH_PODS -> Color(0xFF2E7D32)
                    BleConnectionState.SCANNING,
                    BleConnectionState.CONNECTING,
                    BleConnectionState.HANDSHAKING -> Color(0xFFF57F17)
                    BleConnectionState.ERROR -> Color(0xFFC62828)
                    BleConnectionState.DISCONNECTED -> Color(0xFF455A64)
                }
                StatusBadge(text = bleBadgeText, backgroundColor = bleBadgeColor)

                // Right Pod Badge
                PodBadge(
                    podName = "Right Pod",
                    mac = connectedRightMac,
                    connected = connectedRightMac != null || bleState == BleConnectionState.CONNECTED_RIGHT_ONLY || bleState == BleConnectionState.CONNECTED_BOTH_PODS
                )

                // Left Pod Badge
                PodBadge(
                    podName = "Left Pod",
                    mac = connectedLeftMac,
                    connected = connectedLeftMac != null || bleState == BleConnectionState.CONNECTED_BOTH_PODS
                )

                // MyWhoosh TCP Client Status
                StatusBadge(
                    text = if (hasMyWhooshClient) "MyWhoosh: Connected" else "MyWhoosh: Waiting for client...",
                    backgroundColor = if (hasMyWhooshClient) Color(0xFF1565C0) else Color(0xFF37474F)
                )
            }
        }
    }
}

@Composable
fun StatusBadge(text: String, backgroundColor: Color) {
    Box(
        modifier = Modifier
            .background(color = backgroundColor, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text = text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PodBadge(podName: String, mac: String?, connected: Boolean) {
    val display = if (connected && !mac.isNullOrBlank()) {
        "$podName (${mac.takeLast(5)}): Connected"
    } else if (connected) {
        "$podName: Connected"
    } else {
        "$podName: Off"
    }
    StatusBadge(text = display, backgroundColor = if (connected) Color(0xFF2E7D32) else Color(0xFF455A64))
}

// ---------------------------------------------------------------------------
// 2. Interactive Virtual Zwift Click v2 Simulator Card
// ---------------------------------------------------------------------------

@Composable
fun VirtualZwiftClickSimulatorCard(
    mode: OperatingMode,
    customBindings: Map<ModeButtonBindingKey, MappedTargetAction>,
    onSimulateGesture: (ClickButton, PressType) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Interactive Virtual Zwift Click v2 Simulator",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "Test every button, mode, and action directly from the desktop UI (no physical controller required!)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Interactive Gamepad Visual Cluster
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Pod Quick Cluster (D-Pad + Rocker)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Left Pod", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    // Top Rocker '–'
                    QuickTriggerPadButton(
                        button = ClickButton.SHIFT_DOWN_LEFT,
                        onClick = { onSimulateGesture(ClickButton.SHIFT_DOWN_LEFT, PressType.SINGLE_CLICK) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // D-Pad Up
                    QuickTriggerPadButton(
                        button = ClickButton.UP,
                        onClick = { onSimulateGesture(ClickButton.UP, PressType.SINGLE_CLICK) }
                    )
                    // D-Pad Left, Right
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickTriggerPadButton(
                            button = ClickButton.LEFT,
                            onClick = { onSimulateGesture(ClickButton.LEFT, PressType.SINGLE_CLICK) }
                        )
                        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Text("D-Pad", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        QuickTriggerPadButton(
                            button = ClickButton.RIGHT,
                            onClick = { onSimulateGesture(ClickButton.RIGHT, PressType.SINGLE_CLICK) }
                        )
                    }
                    // D-Pad Down
                    QuickTriggerPadButton(
                        button = ClickButton.DOWN,
                        onClick = { onSimulateGesture(ClickButton.DOWN, PressType.SINGLE_CLICK) }
                    )
                }

                VerticalDivider(
                    modifier = Modifier
                        .height(180.dp)
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Right Pod Quick Cluster (Colorful Buttons + Rocker)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Right Pod", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    // Top Rocker '+'
                    QuickTriggerPadButton(
                        button = ClickButton.SHIFT_UP_RIGHT,
                        onClick = { onSimulateGesture(ClickButton.SHIFT_UP_RIGHT, PressType.SINGLE_CLICK) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    // Top Button 'Z' (Orange)
                    QuickTriggerPadButton(
                        button = ClickButton.Z,
                        onClick = { onSimulateGesture(ClickButton.Z, PressType.SINGLE_CLICK) }
                    )
                    // Middle Buttons 'Y' (Yellow) & 'B' (Green)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickTriggerPadButton(
                            button = ClickButton.Y,
                            onClick = { onSimulateGesture(ClickButton.Y, PressType.SINGLE_CLICK) }
                        )
                        Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Text("Click", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        QuickTriggerPadButton(
                            button = ClickButton.B,
                            onClick = { onSimulateGesture(ClickButton.B, PressType.SINGLE_CLICK) }
                        )
                    }
                    // Bottom Button 'A' (Blue)
                    QuickTriggerPadButton(
                        button = ClickButton.A,
                        onClick = { onSimulateGesture(ClickButton.A, PressType.SINGLE_CLICK) }
                    )
                }
            }

            Text(
                text = "Detailed Simulator Controls (Single Click vs 450ms Long Press):",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )

            // Grid of all 10 buttons with explicit Single Press and Long Press (450ms) triggers
            val allSimulatorButtons = listOf(
                ClickButton.Z,
                ClickButton.A,
                ClickButton.Y,
                ClickButton.B,
                ClickButton.SHIFT_UP_RIGHT,
                ClickButton.SHIFT_DOWN_LEFT,
                ClickButton.LEFT,
                ClickButton.RIGHT,
                ClickButton.UP,
                ClickButton.DOWN
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                allSimulatorButtons.chunked(2).forEach { rowButtons ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowButtons.forEach { btn ->
                            Box(modifier = Modifier.weight(1f)) {
                                SimulatorButtonRow(
                                    mode = mode,
                                    button = btn,
                                    customBindings = customBindings,
                                    onSimulateGesture = onSimulateGesture
                                )
                            }
                        }
                        // Handle odd number of buttons gracefully
                        if (rowButtons.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickTriggerPadButton(button: ClickButton, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(Color(button.badgeColorHex), shape = CircleShape)
            .clickable { onClick() }
            .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = button.shortBadge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
fun SimulatorButtonRow(
    mode: OperatingMode,
    button: ClickButton,
    customBindings: Map<ModeButtonBindingKey, MappedTargetAction>,
    onSimulateGesture: (ClickButton, PressType) -> Unit
) {
    val singleAction = ButtonMappingStore.resolveAction(mode, button, PressType.SINGLE_CLICK, customBindings)
    val longAction = ButtonMappingStore.resolveAction(mode, button, PressType.LONG_PRESS, customBindings)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(Color(button.badgeColorHex), shape = RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = button.shortBadge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = button.displayName, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(
                        text = "1x: ${singleAction.label.take(24)}",
                        fontSize = 10.sp,
                        color = when {
                            singleAction.myWhooshAction != null -> Color(0xFF64B5F6)
                            singleAction.ankiAction != null -> Color(0xFF81C784)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { onSimulateGesture(button, PressType.SINGLE_CLICK) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 6.dp)
                ) {
                    Text("Single", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = { onSimulateGesture(button, PressType.LONG_PRESS) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 6.dp)
                ) {
                    Text("Hold 450ms", fontSize = 11.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 3. Operating Mode Selector Card
// ---------------------------------------------------------------------------

@Composable
fun ModeSelectorCard(
    selectedMode: OperatingMode,
    onSelectMode: (OperatingMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Operating Mode",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            Text(
                text = "Select your workflow. Each mode preserves its own independent button layout:",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OperatingMode.entries.forEach { mode ->
                val isSelected = mode == selectedMode
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectMode(mode) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${if (isSelected) "● " else "○ "}${mode.name.replace('_', ' ')}: ${mode.shortTitle}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Text(
                                    text = "ACTIVE",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = mode.subtitle,
                            fontSize = 12.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 4. AnkiConnect (:8765) & OpenBikeControl (:21587) Card
// ---------------------------------------------------------------------------

@Composable
fun NetworkingTestCard(
    config: AppConfiguration,
    onConfigChange: (AppConfiguration) -> Unit,
    hasMyWhooshClient: Boolean,
    onTestAnkiAction: (AnkiAction) -> Unit,
    onTestMyWhooshAction: (MyWhooshAction) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "AnkiConnect (:8765) & OpenBikeControl (:21587)",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            Text(
                text = "Configure AnkiConnect target address and trigger manual test dispatches to verify connectivity.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Host and Port input fields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = config.ubuntuAnkiConnectHost,
                    onValueChange = { onConfigChange(config.copy(ubuntuAnkiConnectHost = it.trim())) },
                    label = { Text("AnkiConnect Host (e.g. 127.0.0.1 or LAN IP)") },
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = config.ubuntuAnkiConnectPort.toString(),
                    onValueChange = { input ->
                        val port = input.filter { it.isDigit() }.toIntOrNull() ?: 8765
                        onConfigChange(config.copy(ubuntuAnkiConnectPort = port))
                    },
                    label = { Text("Port") },
                    modifier = Modifier.width(110.dp)
                )
            }

            // Test dispatch action buttons
            Text(text = "Manual Connection & Shifting Tests:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onTestAnkiAction(AnkiAction.FLIP_CARD) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Test Anki Flip (guiShowAnswer)", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { onTestAnkiAction(AnkiAction.FLIP_OR_GOOD) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Test Good (3)", fontSize = 11.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onTestMyWhooshAction(MyWhooshAction.SHIFT_UP) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Test Shift Up (+)", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = { onTestMyWhooshAction(MyWhooshAction.SHIFT_DOWN) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Test Shift Down (–)", fontSize = 11.sp)
                }
            }

            // Server status summary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "OpenBikeControl Server: Port ${config.openBikeServerPort} • mDNS _openbikecontrol._tcp.local.",
                    color = Color(0xFFC9D1D9),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (hasMyWhooshClient) "MyWhoosh: CONNECTED" else "MyWhoosh: WAITING",
                    color = if (hasMyWhooshClient) Color(0xFF7EE787) else Color(0xFFFFA657),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 5. Desktop Options Card
// ---------------------------------------------------------------------------

@Composable
fun DesktopOptionsCard(
    config: AppConfiguration,
    onConfigChange: (AppConfiguration) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Desktop Hardware & Feedback Options",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )

            // Right-Pod Only Toggle
            OptionToggleRow(
                title = "1-Controller Default (Right Pod Only)",
                subtitle = "Zero 24h sleep lock; switch off to enable both Left and Right pods",
                checked = config.rightPodOnlyNoZwift,
                onCheckedChange = { onConfigChange(config.copy(rightPodOnlyNoZwift = it)) }
            )

            // Top Z for Shift Down
            if (config.rightPodOnlyNoZwift) {
                OptionToggleRow(
                    title = "Top 'Z' Button Shifts Down",
                    subtitle = "Instant shifting in 1-pod mode: '+' = Shift Up, 'Z' = Shift Down, Y/B/A = Anki",
                    checked = config.useZForShiftDownInSinglePodMode,
                    onCheckedChange = { onConfigChange(config.copy(useZForShiftDownInSinglePodMode = it)) }
                )
            }

            // Floating Mini HUD Pill
            OptionToggleRow(
                title = "Floating Mini HUD Pill",
                subtitle = "Always-on-top transparent mini HUD pill floating over MyWhoosh and Anki",
                checked = config.floatingHudPillEnabled,
                onCheckedChange = { onConfigChange(config.copy(floatingHudPillEnabled = it)) }
            )

            // Audio Feedback Cues
            OptionToggleRow(
                title = "Audio Feedback Cues",
                subtitle = "Plays subtle audio confirmation tones when shifting or reviewing cards",
                checked = config.audioFeedbackEnabled,
                onCheckedChange = { onConfigChange(config.copy(audioFeedbackEnabled = it)) }
            )

            // MyWhoosh Keyboard Robot Fallback
            OptionToggleRow(
                title = "Keyboard Robot Fallback (I / K)",
                subtitle = "Injects keystrokes 'I' (Shift Up) and 'K' (Shift Down) via java.awt.Robot when no TCP client is connected",
                checked = config.useMyShiftTouchFallbackWhenNoClient,
                onCheckedChange = { onConfigChange(config.copy(useMyShiftTouchFallbackWhenNoClient = it)) }
            )
        }
    }
}

@Composable
fun OptionToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

// ---------------------------------------------------------------------------
// 6. Color-Coded Custom Button Mapping Editor
// ---------------------------------------------------------------------------

@Composable
fun CustomButtonMappingEditorCard(
    activeMode: OperatingMode,
    rightPodOnly: Boolean,
    customBindings: Map<ModeButtonBindingKey, MappedTargetAction>,
    onUpdateBindings: (Map<ModeButtonBindingKey, MappedTargetAction>) -> Unit
) {
    var editingMode by remember(activeMode) { mutableStateOf(activeMode) }

    val buttonsToConfigure = remember(rightPodOnly) {
        val rightButtons = listOf(
            ClickButton.Z,
            ClickButton.A,
            ClickButton.Y,
            ClickButton.B,
            ClickButton.SHIFT_UP_RIGHT
        )
        val leftButtons = listOf(
            ClickButton.SHIFT_DOWN_LEFT,
            ClickButton.LEFT,
            ClickButton.RIGHT,
            ClickButton.UP,
            ClickButton.DOWN
        )
        if (rightPodOnly) rightButtons else rightButtons + leftButtons
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Button Mapping Editor",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "Custom per-mode action overrides (saved to ~/.ankiride/settings.properties)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = {
                        val cleared = customBindings.filterKeys { it.mode != editingMode }
                        onUpdateBindings(cleared)
                        DiagnosticLogger.info(LogCategory.ROUTER, "Reset ${editingMode.shortTitle} bindings to default")
                    }
                ) {
                    Text("Reset Mode to Defaults", fontSize = 11.sp)
                }
            }

            // Mode Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OperatingMode.entries.forEach { mode ->
                    FilterChip(
                        selected = editingMode == mode,
                        onClick = { editingMode = mode },
                        label = {
                            Text(
                                text = if (mode == activeMode) "★ ${mode.shortTitle}" else mode.shortTitle,
                                fontSize = 12.sp,
                                fontWeight = if (editingMode == mode) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // 1-Tap Presets for Combined Modes
            if (editingMode == OperatingMode.MODE_1_TABLET_SPLIT_SCREEN ||
                editingMode == OperatingMode.MODE_2_TABLET_ANKI_PHONE_MYWHOOSH ||
                editingMode == OperatingMode.MODE_5_UBUNTU_ANKICONNECT
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val updated = customBindings.toMutableMap()
                            updated[ModeButtonBindingKey(editingMode, ClickButton.Z, PressType.SINGLE_CLICK)] = MappedTargetAction.MW_SHIFT_UP
                            updated[ModeButtonBindingKey(editingMode, ClickButton.A, PressType.SINGLE_CLICK)] = MappedTargetAction.MW_SHIFT_DOWN
                            updated[ModeButtonBindingKey(editingMode, ClickButton.Y, PressType.SINGLE_CLICK)] = MappedTargetAction.ANKI_FLIP_OR_AGAIN
                            updated[ModeButtonBindingKey(editingMode, ClickButton.B, PressType.SINGLE_CLICK)] = MappedTargetAction.ANKI_FLIP_OR_GOOD
                            onUpdateBindings(updated)
                            DiagnosticLogger.info(LogCategory.ROUTER, "Applied Preset: Z/A Shift • Y/B Anki")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Preset: Z/A Shift • Y/B Anki", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val updated = customBindings.toMutableMap()
                            updated[ModeButtonBindingKey(editingMode, ClickButton.Z, PressType.SINGLE_CLICK)] = MappedTargetAction.MW_SHIFT_UP
                            updated[ModeButtonBindingKey(editingMode, ClickButton.B, PressType.SINGLE_CLICK)] = MappedTargetAction.MW_SHIFT_DOWN
                            updated[ModeButtonBindingKey(editingMode, ClickButton.Y, PressType.SINGLE_CLICK)] = MappedTargetAction.ANKI_FLIP_OR_AGAIN
                            updated[ModeButtonBindingKey(editingMode, ClickButton.A, PressType.SINGLE_CLICK)] = MappedTargetAction.ANKI_FLIP_OR_GOOD
                            onUpdateBindings(updated)
                            DiagnosticLogger.info(LogCategory.ROUTER, "Applied Preset: Z/B Shift • Y/A Anki")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Preset: Z/B Shift • Y/A Anki", fontSize = 11.sp)
                    }
                }
            }

            // List of ColorCodedButtonCards
            buttonsToConfigure.forEach { button ->
                ColorCodedButtonCard(
                    mode = editingMode,
                    button = button,
                    customBindings = customBindings,
                    onSelectAction = { pressType, target ->
                        val key = ModeButtonBindingKey(editingMode, button, pressType)
                        val updated = if (target == null) {
                            customBindings - key
                        } else {
                            customBindings + (key to target)
                        }
                        onUpdateBindings(updated)
                    }
                )
            }
        }
    }
}

@Composable
fun ColorCodedButtonCard(
    mode: OperatingMode,
    button: ClickButton,
    customBindings: Map<ModeButtonBindingKey, MappedTargetAction>,
    onSelectAction: (PressType, MappedTargetAction?) -> Unit
) {
    val singleResolved = ButtonMappingStore.resolveAction(mode, button, PressType.SINGLE_CLICK, customBindings)
    val longResolved = ButtonMappingStore.resolveAction(mode, button, PressType.LONG_PRESS, customBindings)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(button.badgeColorHex), shape = RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = button.shortBadge,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = button.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            GestureActionPickerRow(
                gestureLabel = "Single Press",
                resolvedAction = singleResolved,
                isCustom = customBindings.containsKey(ModeButtonBindingKey(mode, button, PressType.SINGLE_CLICK)),
                onSelect = { onSelectAction(PressType.SINGLE_CLICK, it) }
            )

            GestureActionPickerRow(
                gestureLabel = "Long Press (450ms)",
                resolvedAction = longResolved,
                isCustom = customBindings.containsKey(ModeButtonBindingKey(mode, button, PressType.LONG_PRESS)),
                onSelect = { onSelectAction(PressType.LONG_PRESS, it) }
            )
        }
    }
}

@Composable
fun GestureActionPickerRow(
    gestureLabel: String,
    resolvedAction: MappedTargetAction,
    isCustom: Boolean,
    onSelect: (MappedTargetAction?) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = gestureLabel,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = resolvedAction.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    resolvedAction.myWhooshAction != null -> Color(0xFF64B5F6)
                    resolvedAction.ankiAction != null -> Color(0xFF81C784)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Box {
            OutlinedButton(onClick = { menuExpanded = true }) {
                Text(if (isCustom) "Custom ▾" else "Change ▾", fontSize = 11.sp)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Reset to Mode Default") },
                    onClick = {
                        onSelect(null)
                        menuExpanded = false
                    }
                )
                MappedTargetAction.entries.forEach { target ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = target.label,
                                color = when {
                                    target.myWhooshAction != null -> Color(0xFF64B5F6)
                                    target.ankiAction != null -> Color(0xFF81C784)
                                    else -> Color.Unspecified
                                }
                            )
                        },
                        onClick = {
                            onSelect(target)
                            menuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 7. Live Diagnostic Log Viewer
// ---------------------------------------------------------------------------

@Composable
fun DiagnosticLogViewerCard() {
    val logs by DiagnosticLogger.logs.collectAsState()
    var selectedCategory by remember { mutableStateOf<LogCategory?>(null) }

    val filteredLogs = remember(logs, selectedCategory) {
        if (selectedCategory == null) logs else logs.filter { it.category == selectedCategory }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Live Diagnostic Log (${filteredLogs.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "Real-time BLE packets, key injections, and OpenBikeControl events",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val text = DiagnosticLogger.formatForClipboard(selectedCategory)
                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                            clipboard.setContents(StringSelection(text), null)
                            DiagnosticLogger.info(LogCategory.SYSTEM, "Copied ${filteredLogs.size} logs to system clipboard")
                        }
                    ) {
                        Text("Copy Logs", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { DiagnosticLogger.clear() }
                    ) {
                        Text("Clear", fontSize = 12.sp)
                    }
                }
            }

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("ALL", fontSize = 11.sp) }
                )
                listOf(
                    LogCategory.BLE,
                    LogCategory.PACKET,
                    LogCategory.ANKI,
                    LogCategory.MYWHOOSH,
                    LogCategory.ROUTER,
                    LogCategory.SYSTEM
                ).forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = if (selectedCategory == category) null else category },
                        label = { Text(category.name, fontSize = 11.sp) }
                    )
                }
            }

            // Monospace Scrollable Log Box
            val logScrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(Color(0xFF0D1117), shape = RoundedCornerShape(8.dp))
                    .padding(10.dp)
                    .verticalScroll(logScrollState)
            ) {
                if (filteredLogs.isEmpty()) {
                    Text(
                        text = "No log entries yet. Click 'Start Bridge' or simulate button gestures to see live events.",
                        color = Color(0xFF8B949E),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        filteredLogs.takeLast(150).reversed().forEach { entry ->
                            val color = when (entry.level) {
                                LogLevel.ERROR -> Color(0xFFFF7B72)
                                LogLevel.WARN -> Color(0xFFD29922)
                                LogLevel.INFO -> Color(0xFFC9D1D9)
                            }
                            Text(
                                text = "[${entry.formattedTime}] [${entry.category.name}] ${entry.message}",
                                color = color,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.ankiride.bridge.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.BleConnectionState
import com.ankiride.bridge.core.OperatingMode
import com.ankiride.bridge.mywhoosh.MyWhooshDiscovery
import com.ankiride.bridge.service.BridgeAccessibilityService
import com.ankiride.bridge.service.BridgeForegroundService

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions updated
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBlePermissions()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnkiRideDashboard(
                        onRequestPermissions = { requestBlePermissions() },
                        onOpenAccessibility = { openAccessibilitySettings() },
                        onOpenBatteryOpt = { requestIgnoreBatteryOptimizations() }
                    )
                }
            }
        }
    }

    private fun requestBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT
            )
            val missing = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                permissionLauncher.launch(missing.toTypedArray())
            }
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun requestIgnoreBatteryOptimizations() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}

@Composable
fun AnkiRideDashboard(
    onRequestPermissions: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenBatteryOpt: () -> Unit
) {
    val context = LocalContext.current
    val isRunning by BridgeForegroundService.isRunningFlow.collectAsState()
    val bleState by BridgeForegroundService.connectionStateFlow.collectAsState()

    var selectedMode by remember { mutableStateOf(OperatingMode.MODE_1_TABLET_BOTH) }
    var rightPodOnly by remember { mutableStateOf(false) }
    var targetPhoneIp by remember { mutableStateOf("192.168.1.50") }
    var pinnedLeftMac by remember { mutableStateOf("") }
    var pinnedRightMac by remember { mutableStateOf("") }
    var useTouchFallback by remember { mutableStateOf(false) }

    val discovery = remember { MyWhooshDiscovery(context) }
    val discoveredIps by discovery.discoveredIps.collectAsState()

    DisposableEffect(selectedMode) {
        if (selectedMode == OperatingMode.MODE_2_TABLET_ANKI_PHONE_MYWHOOSH) {
            discovery.startDiscovery()
        } else {
            discovery.stopDiscovery()
        }
        onDispose {
            discovery.stopDiscovery()
        }
    }

    val scrollState = rememberScrollState()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "AnkiRide Bridge",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // 1. Connection Status Card
            ConnectionStatusCard(
                bleState = bleState,
                isRunning = isRunning,
                onToggleService = {
                    if (isRunning) {
                        BridgeForegroundService.stopService(context)
                    } else {
                        val config = AppConfiguration(
                            mode = selectedMode,
                            rightPodOnlyMode = rightPodOnly,
                            targetMyWhooshHost = if (selectedMode == OperatingMode.MODE_2_TABLET_ANKI_PHONE_MYWHOOSH) targetPhoneIp else "127.0.0.1",
                            useAccessibilityTapFallbackForMyWhoosh = useTouchFallback,
                            pinnedLeftPodMac = pinnedLeftMac.takeIf { it.isNotBlank() },
                            pinnedRightPodMac = pinnedRightMac.takeIf { it.isNotBlank() }
                        )
                        BridgeForegroundService.startService(context, config)
                    }
                }
            )

            // 2. 4-Mode Selector
            Text(
                text = "Operating Mode",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
            ModeSelector(
                selectedMode = selectedMode,
                onModeSelected = { selectedMode = it }
            )

            // Mode 2 specific configuration (Pixel 10 LAN IP & mDNS)
            if (selectedMode == OperatingMode.MODE_2_TABLET_ANKI_PHONE_MYWHOOSH) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Pixel 10 MyWhoosh Target IP",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = targetPhoneIp,
                            onValueChange = { targetPhoneIp = it },
                            label = { Text("Phone IPv4 Address") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (discoveredIps.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "Discovered on LAN (mDNS):", fontSize = 14.sp)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                discoveredIps.forEach { ip ->
                                    Button(
                                        onClick = { targetPhoneIp = ip },
                                        contentPadding = ButtonDefaults.TextButtonContentPadding
                                    ) {
                                        Text(ip)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Hardware & Pod Controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Hardware Controller Configuration",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Right-Pod-Only Mode", fontWeight = FontWeight.Medium)
                            Text(
                                text = "Ignores left pod (prevents 24h sleep lock); double-press '+' to shift down",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = rightPodOnly,
                            onCheckedChange = { rightPodOnly = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "MyShift Touch Tap Fallback", fontWeight = FontWeight.Medium)
                            Text(
                                text = "Uses AccessibilityService touch emulation if OpenBikeControl port is blocked",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useTouchFallback,
                            onCheckedChange = { useTouchFallback = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = pinnedLeftMac,
                            onValueChange = { pinnedLeftMac = it },
                            label = { Text("Left Pod MAC") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = pinnedRightMac,
                            onValueChange = { pinnedRightMac = it },
                            label = { Text("Right Pod MAC") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 4. Checklist & Permissions
            PermissionsChecklistCard(
                onRequestPermissions = onRequestPermissions,
                onOpenAccessibility = onOpenAccessibility,
                onOpenBatteryOpt = onOpenBatteryOpt
            )
        }
    }
}

@Composable
fun ConnectionStatusCard(
    bleState: BleConnectionState,
    isRunning: Boolean,
    onToggleService: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isRunning) "BRIDGE ACTIVE" else "BRIDGE STOPPED",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onToggleService,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isRunning) "Stop Bridge" else "Start Bridge")
                }
            }

            when (bleState) {
                is BleConnectionState.Disconnected -> {
                    Text("Controller: Disconnected", fontSize = 14.sp)
                }
                is BleConnectionState.Scanning -> {
                    Text("Controller: Scanning for Zwift Click v2...", fontSize = 14.sp)
                }
                is BleConnectionState.Connected -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PodBadge("Left Pod", bleState.leftPodConnected)
                        PodBadge("Right Pod", bleState.rightPodConnected)
                        if (bleState.batteryPercent != null) {
                            Text("Battery: ${bleState.batteryPercent}%", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                is BleConnectionState.Error -> {
                    Text("Error: ${bleState.reason}", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun PodBadge(label: String, connected: Boolean) {
    Box(
        modifier = Modifier
            .background(
                color = if (connected) Color(0xFF2E7D32) else Color(0xFFC62828),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "$label: ${if (connected) "Connected" else "Disconnected"}",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ModeSelector(
    selectedMode: OperatingMode,
    onModeSelected: (OperatingMode) -> Unit
) {
    val modes = listOf(
        Triple(
            OperatingMode.MODE_1_TABLET_BOTH,
            "Tablet Split-Screen",
            "Runs MyWhoosh + native AnkiDroid side-by-side. Routes shifts to 127.0.0.1; A/B/Y/Z clicks AnkiDroid Reviewer."
        ),
        Triple(
            OperatingMode.MODE_2_TABLET_ANKI_PHONE_MYWHOOSH,
            "Tablet Anki + Phone MyWhoosh",
            "AnkiDroid full-screen on Tablet; MyWhoosh on Pixel 10. Routes shifts over LAN Wi-Fi to phone."
        ),
        Triple(
            OperatingMode.MODE_3_ANKI_ONLY,
            "Anki-Only (Study)",
            "Pure study mode. All 10 buttons mapped to AnkiDroid card review, audio replay, and navigation."
        ),
        Triple(
            OperatingMode.MODE_4_MYWHOOSH_ONLY,
            "MyWhoosh-Only (Ride)",
            "Pure ride mode. All 10 buttons mapped to MyWhoosh shifting, steering, UI toggle, and emotes."
        )
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        modes.forEach { (mode, title, description) ->
            val isSelected = mode == selectedMode
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onModeSelected(mode) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 1.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        fontSize = 13.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionsChecklistCard(
    onRequestPermissions: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenBatteryOpt: () -> Unit
) {
    val context = LocalContext.current
    val a11yActive = BridgeAccessibilityService.activeInstance != null
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val batteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    } else true

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "System Setup & Permissions",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )

            // Accessibility Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (a11yActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (a11yActive) Color(0xFF4CAF50) else Color(0xFFFFA000)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Accessibility Service", fontSize = 14.sp)
                }
                OutlinedButton(onClick = onOpenAccessibility) {
                    Text(if (a11yActive) "Enabled" else "Enable")
                }
            }

            // Battery Optimization
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (batteryIgnored) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (batteryIgnored) Color(0xFF4CAF50) else Color(0xFFFFA000)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Battery Optimization", fontSize = 14.sp)
                }
                OutlinedButton(onClick = onOpenBatteryOpt) {
                    Text(if (batteryIgnored) "Exempt" else "Exempt")
                }
            }

            // Bluetooth Scan/Connect
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Bluetooth LE Permissions", fontSize = 14.sp)
                }
                OutlinedButton(onClick = onRequestPermissions) {
                    Text("Check / Grant")
                }
            }
        }
    }
}

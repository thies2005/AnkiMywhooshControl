package com.ankiride.bridge.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ankiride.bridge.anki.AnkiAccessibilityBridge
import com.ankiride.bridge.ble.ZwiftBleController
import com.ankiride.bridge.core.*
import com.ankiride.bridge.service.BridgeAccessibilityService
import com.ankiride.bridge.service.BridgeForegroundService
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val permissionRefreshTrigger = MutableStateFlow(0)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permissionRefreshTrigger.value += 1
        DiagnosticLogger.info(LogCategory.PERMS, "BLE Permission result: $grants")
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
                    val refreshCount by permissionRefreshTrigger.collectAsState()
                    AnkiRideDashboard(
                        permissionRefreshCount = refreshCount,
                        onRequestPermissions = { requestBlePermissions() },
                        onOpenAccessibility = { openAccessibilitySettings() },
                        onOpenBatteryOpt = { requestIgnoreBatteryOptimizations() }
                    )
                }
            }
        }
    }

    private fun requestBlePermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            permissionRefreshTrigger.value += 1
        }
    }

    private fun openAccessibilitySettings() {
        DiagnosticLogger.info(LogCategory.PERMS, "Opening Android Accessibility Settings")
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun requestIgnoreBatteryOptimizations() {
        DiagnosticLogger.info(LogCategory.PERMS, "Requesting battery optimization exemption")
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

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    if (BridgeAccessibilityService.activeInstance != null || BridgeAccessibilityService.isConnectedFlow.value) {
        return true
    }
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.contains(context.packageName, ignoreCase = true)
}

fun isBatteryOptimizationExempt(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

fun areBlePermissionsGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}

private const val PREFS_NAME = "ankiride_prefs"
private const val KEY_CUSTOM_BINDINGS = "custom_button_bindings"

private fun loadSavedCustomBindings(context: Context): Map<ButtonBindingKey, MappedTargetAction> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val set = prefs.getStringSet(KEY_CUSTOM_BINDINGS, null) ?: return emptyMap()
    val map = mutableMapOf<ButtonBindingKey, MappedTargetAction>()
    for (entry in set) {
        val parts = entry.split("=")
        if (parts.size == 2) {
            val keyParts = parts[0].split(":")
            if (keyParts.size == 2) {
                try {
                    val btn = ClickButton.valueOf(keyParts[0])
                    val press = PressType.valueOf(keyParts[1])
                    val target = MappedTargetAction.valueOf(parts[1])
                    map[ButtonBindingKey(btn, press)] = target
                } catch (_: Exception) {}
            }
        }
    }
    return map
}

private fun saveCustomBindings(context: Context, bindings: Map<ButtonBindingKey, MappedTargetAction>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val set = bindings.map { "${it.key.button.name}:${it.key.pressType.name}=${it.value.name}" }.toSet()
    prefs.edit().putStringSet(KEY_CUSTOM_BINDINGS, set).apply()
}

@Composable
fun AnkiRideDashboard(
    permissionRefreshCount: Int,
    onRequestPermissions: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenBatteryOpt: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isRunning by BridgeForegroundService.isRunningFlow.collectAsState()
    val bleState by BridgeForegroundService.connectionStateFlow.collectAsState()
    val a11yServiceConnected by BridgeAccessibilityService.isConnectedFlow.collectAsState()
    val lastRightMac by ZwiftBleController.lastRightMac.collectAsState()
    val lastLeftMac by ZwiftBleController.lastLeftMac.collectAsState()

    var selectedMode by remember { mutableStateOf(OperatingMode.MODE_1_TABLET_SPLIT_SCREEN) }
    var rightPodOnly by remember { mutableStateOf(true) }
    var useZForShiftDown by remember { mutableStateOf(true) }
    var useTouchFallback by remember { mutableStateOf(true) }
    var lockToConnectedPods by remember { mutableStateOf(false) }
    var customBindings by remember { mutableStateOf(loadSavedCustomBindings(context)) }

    // Reactive permission states updated on ON_RESUME and when service state changes
    var a11yEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var batteryExempt by remember { mutableStateOf(isBatteryOptimizationExempt(context)) }
    var bleGranted by remember { mutableStateOf(areBlePermissionsGranted(context)) }

    LaunchedEffect(a11yServiceConnected, permissionRefreshCount) {
        a11yEnabled = isAccessibilityServiceEnabled(context)
        batteryExempt = isBatteryOptimizationExempt(context)
        bleGranted = areBlePermissionsGranted(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                a11yEnabled = isAccessibilityServiceEnabled(context)
                batteryExempt = isBatteryOptimizationExempt(context)
                bleGranted = areBlePermissionsGranted(context)
                DiagnosticLogger.info(
                    LogCategory.PERMS,
                    "ON_RESUME permission check: A11y=$a11yEnabled, BatteryExempt=$batteryExempt, BLE=$bleGranted"
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun buildCurrentConfig(): AppConfiguration {
        return AppConfiguration(
            mode = selectedMode,
            rightPodOnlyNoZwift = rightPodOnly,
            useZForShiftDownInSinglePodMode = useZForShiftDown,
            useMyShiftTouchFallbackWhenNoClient = useTouchFallback,
            pinnedRightPodMac = if (lockToConnectedPods) lastRightMac else null,
            pinnedLeftPodMac = if (lockToConnectedPods) lastLeftMac else null,
            customBindings = customBindings
        )
    }

    // Keep live service config synced when toggles or custom bindings change
    LaunchedEffect(selectedMode, rightPodOnly, useZForShiftDown, useTouchFallback, lockToConnectedPods, customBindings) {
        if (isRunning) {
            BridgeForegroundService.startService(context, buildCurrentConfig())
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
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "100% Standalone OpenBikeControl Server + AnkiDroid Reviewer (No BikeControl or Zwift App Needed)",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 1. Permissions Checklist (Auto-collapses when all 3 are granted!)
            PermissionsChecklistCard(
                a11yEnabled = a11yEnabled,
                batteryExempt = batteryExempt,
                bleGranted = bleGranted,
                onRequestPermissions = onRequestPermissions,
                onOpenAccessibility = onOpenAccessibility,
                onOpenBatteryOpt = onOpenBatteryOpt
            )

            // 2. Connection Status Card
            ConnectionStatusCard(
                bleState = bleState,
                isRunning = isRunning,
                lastRightMac = lastRightMac,
                lastLeftMac = lastLeftMac,
                onToggleService = {
                    if (isRunning) {
                        BridgeForegroundService.stopService(context)
                    } else {
                        BridgeForegroundService.startService(context, buildCurrentConfig())
                    }
                }
            )

            // 3. Operating Mode Selector
            Text(
                text = "Operating Mode",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            ModeSelector(
                selectedMode = selectedMode,
                onModeSelected = { selectedMode = it }
            )

            // 4. Hardware & Shifting Behavior (No manual MAC typing needed)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Hardware & Shifting Options",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "1-Controller Default (Right Pod Only)", fontWeight = FontWeight.Medium)
                            Text(
                                text = "Zero 24h sleep lock; switch off to use both Left & Right pods",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = rightPodOnly,
                            onCheckedChange = { rightPodOnly = it }
                        )
                    }

                    if (rightPodOnly) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Top Z Button Shifts Down", fontWeight = FontWeight.Medium)
                                Text(
                                    text = "Instant shifting: '+' = Shift Up, 'Z' = Shift Down, Y/B/A = Anki",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = useZForShiftDown,
                                onCheckedChange = { useZForShiftDown = it }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "MyShift Touch Tap Fallback", fontWeight = FontWeight.Medium)
                            Text(
                                text = "Taps MyWhoosh screen arrows if OpenBikeControl icon isn't paired",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useTouchFallback,
                            onCheckedChange = { useTouchFallback = it }
                        )
                    }

                    if (lastRightMac != null || lastLeftMac != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Lock to Connected Controller", fontWeight = FontWeight.Medium)
                                Text(
                                    text = "Pin MAC (${lastRightMac ?: lastLeftMac}) so nearby controllers are ignored",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = lockToConnectedPods,
                                onCheckedChange = { lockToConnectedPods = it }
                            )
                        }
                    }
                }
            }

            // 5. Custom Button Mapping Editor
            CustomButtonMappingCard(
                rightPodOnly = rightPodOnly,
                customBindings = customBindings,
                onUpdateBinding = { key, target ->
                    val updated = if (target == null) {
                        customBindings - key
                    } else {
                        customBindings + (key to target)
                    }
                    customBindings = updated
                    saveCustomBindings(context, updated)
                },
                onResetAll = {
                    customBindings = emptyMap()
                    saveCustomBindings(context, emptyMap())
                    DiagnosticLogger.info(LogCategory.ROUTER, "Reset all custom button bindings to default preset")
                }
            )

            // 6. Detailed Live Diagnostic Log Card with Copy, Clear & Window Inspector
            DiagnosticLogCard()
        }
    }
}

@Composable
fun PermissionsChecklistCard(
    a11yEnabled: Boolean,
    batteryExempt: Boolean,
    bleGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenBatteryOpt: () -> Unit
) {
    val allReady = a11yEnabled && batteryExempt && bleGranted
    var expandedWhenReady by remember { mutableStateOf(false) }

    if (allReady && !expandedWhenReady) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expandedWhenReady = true },
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFFA5D6A7)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "All System Permissions Active (Accessibility, Battery & BLE)",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = "Show",
                    color = Color(0xFFA5D6A7),
                    fontSize = 12.sp
                )
            }
        }
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "System Setup & Permissions",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                if (allReady) {
                    Text(
                        text = "Hide",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { expandedWhenReady = false }
                    )
                }
            }

            // Accessibility Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (a11yEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (a11yEnabled) Color(0xFF4CAF50) else Color(0xFFFFA000)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (a11yEnabled) "Accessibility Service (Active)" else "Accessibility Service (Required)",
                        fontSize = 14.sp
                    )
                }
                OutlinedButton(onClick = onOpenAccessibility) {
                    Text(if (a11yEnabled) "Settings" else "Enable")
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
                        imageVector = if (batteryExempt) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (batteryExempt) Color(0xFF4CAF50) else Color(0xFFFFA000)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (batteryExempt) "Battery Unrestricted (Active)" else "Battery Optimization (Restricting)",
                        fontSize = 14.sp
                    )
                }
                OutlinedButton(onClick = onOpenBatteryOpt) {
                    Text(if (batteryExempt) "Exempt ✓" else "Allow Background")
                }
            }

            // Bluetooth LE Permissions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (bleGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (bleGranted) Color(0xFF4CAF50) else Color(0xFFFFA000)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (bleGranted) "Bluetooth LE Permissions (Granted)" else "Bluetooth LE Permissions (Missing)",
                        fontSize = 14.sp
                    )
                }
                OutlinedButton(onClick = onRequestPermissions) {
                    Text(if (bleGranted) "Granted ✓" else "Grant")
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(
    bleState: BleConnectionState,
    isRunning: Boolean,
    lastRightMac: String?,
    lastLeftMac: String?,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isRunning) "OPENBIKECONTROL SERVER ACTIVE" else "BRIDGE STOPPED",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isRunning) "Port 21587 • mDNS _openbikecontrol._tcp" else "Tap Start to connect Zwift Click & advertise to MyWhoosh",
                        fontSize = 12.sp,
                        color = if (isRunning) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onToggleService,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isRunning) "Stop Bridge" else "Start Bridge")
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (bleState) {
                    BleConnectionState.DISCONNECTED -> Text("Controller: Disconnected", fontSize = 13.sp)
                    BleConnectionState.SCANNING -> Text("Controller: Scanning for Zwift Click v2...", fontSize = 13.sp)
                    BleConnectionState.CONNECTING, BleConnectionState.HANDSHAKING -> Text("Controller: Sending 'RideOn' Handshake...", fontSize = 13.sp)
                    BleConnectionState.CONNECTED_RIGHT_ONLY -> {
                        PodBadge("Right Pod ${lastRightMac?.takeLast(5) ?: ""}", connected = true)
                        PodBadge("Left Pod", connected = false)
                    }
                    BleConnectionState.CONNECTED_BOTH_PODS -> {
                        PodBadge("Right Pod ${lastRightMac?.takeLast(5) ?: ""}", connected = true)
                        PodBadge("Left Pod ${lastLeftMac?.takeLast(5) ?: ""}", connected = true)
                    }
                    BleConnectionState.ERROR -> Text(
                        "Controller: Connection Error (See Live Log below)",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
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
                color = if (connected) Color(0xFF2E7D32) else Color(0xFF616161),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "$label: ${if (connected) "Connected" else "Off"}",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CustomButtonMappingCard(
    rightPodOnly: Boolean,
    customBindings: Map<ButtonBindingKey, MappedTargetAction>,
    onUpdateBinding: (ButtonBindingKey, MappedTargetAction?) -> Unit,
    onResetAll: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val buttonsToConfigure = remember(rightPodOnly) {
        val coreRight = listOf(ClickButton.SHIFT_UP_RIGHT, ClickButton.Z, ClickButton.A, ClickButton.B, ClickButton.Y)
        val coreLeft = listOf(ClickButton.SHIFT_DOWN_LEFT, ClickButton.LEFT, ClickButton.RIGHT, ClickButton.UP, ClickButton.DOWN)
        if (rightPodOnly) coreRight else coreRight + coreLeft
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Custom Button Mapping (${if (customBindings.isEmpty()) "Using Default Preset" else "${customBindings.size} Custom Overrides"})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = if (expanded) "Tap any button below to customize Single Click or Long Press" else "Tap to customize what each controller button triggers",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = if (expanded) "Collapse ▲" else "Customize ▼",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onResetAll) {
                        Text("Reset All to Default Preset", fontSize = 12.sp)
                    }
                }

                buttonsToConfigure.forEach { button ->
                    ButtonBindingRow(
                        button = button,
                        pressType = PressType.SINGLE_CLICK,
                        currentOverride = customBindings[ButtonBindingKey(button, PressType.SINGLE_CLICK)],
                        onSelect = { onUpdateBinding(ButtonBindingKey(button, PressType.SINGLE_CLICK), it) }
                    )
                    ButtonBindingRow(
                        button = button,
                        pressType = PressType.LONG_PRESS,
                        currentOverride = customBindings[ButtonBindingKey(button, PressType.LONG_PRESS)],
                        onSelect = { onUpdateBinding(ButtonBindingKey(button, PressType.LONG_PRESS), it) }
                    )
                }
            }
        }
    }
}

@Composable
fun ButtonBindingRow(
    button: ClickButton,
    pressType: PressType,
    currentOverride: MappedTargetAction?,
    onSelect: (MappedTargetAction?) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val gestureLabel = if (pressType == PressType.SINGLE_CLICK) "Single Click" else "Long Press (450ms)"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${button.displayName} • $gestureLabel", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                text = currentOverride?.label ?: "Default Mode Mapping",
                fontSize = 11.sp,
                color = if (currentOverride != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            OutlinedButton(onClick = { menuExpanded = true }) {
                Text(if (currentOverride != null) "Change" else "Default", fontSize = 12.sp)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Use Default Mode Mapping") },
                    onClick = {
                        onSelect(null)
                        menuExpanded = false
                    }
                )
                MappedTargetAction.entries.forEach { target ->
                    DropdownMenuItem(
                        text = { Text(target.label) },
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

@Composable
fun DiagnosticLogCard() {
    val context = LocalContext.current
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
            modifier = Modifier.padding(16.dp),
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
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Real-time BLE packets, Anki clicks & OpenBikeControl events",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action Buttons: Copy, Clear, Inspect Windows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val text = DiagnosticLogger.formatForClipboard(selectedCategory)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("AnkiRide Diagnostic Log", text))
                        Toast.makeText(context, "Copied ${filteredLogs.size} log lines to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Copy Logs", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        DiagnosticLogger.clear()
                        Toast.makeText(context, "Cleared diagnostic log", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear Logs", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        AnkiAccessibilityBridge.dumpInteractiveWindowsToLog(BridgeAccessibilityService.activeInstance)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Inspect Windows", fontSize = 12.sp)
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
                LogCategory.entries.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = if (selectedCategory == category) null else category },
                        label = { Text(category.name, fontSize = 11.sp) }
                    )
                }
            }

            // Monospace Log Viewer Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(Color(0xFF0D1117), shape = RoundedCornerShape(8.dp))
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (filteredLogs.isEmpty()) {
                    Text(
                        text = "No log entries yet. Tap 'Start Bridge' or 'Inspect Windows' to see live events.",
                        color = Color(0xFF8B949E),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        filteredLogs.takeLast(120).reversed().forEach { entry ->
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

@Composable
fun ModeSelector(
    selectedMode: OperatingMode,
    onModeSelected: (OperatingMode) -> Unit
) {
    val modes = listOf(
        Triple(
            OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
            "Tablet Split-Screen",
            "Runs MyWhoosh + native AnkiDroid side-by-side. Connects over 127.0.0.1; A/B/Y/Z clicks AnkiDroid Reviewer."
        ),
        Triple(
            OperatingMode.MODE_2_TABLET_ANKI_PHONE_MYWHOOSH,
            "Tablet Anki + Phone MyWhoosh",
            "AnkiDroid full-screen on Tablet; MyWhoosh on phone. MyWhoosh connects over local Wi-Fi LAN via mDNS."
        ),
        Triple(
            OperatingMode.MODE_3_ANKI_ONLY,
            "Anki-Only (Study)",
            "Pure study mode. All buttons mapped to AnkiDroid card review and undo."
        ),
        Triple(
            OperatingMode.MODE_4_MYWHOOSH_ONLY,
            "MyWhoosh-Only (Ride)",
            "Pure ride mode. All buttons mapped to MyWhoosh shifting, steering, UI toggle, and emotes."
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
                        fontSize = 15.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        fontSize = 12.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

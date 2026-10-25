package com.ankiride.bridge

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ankiride.bridge.anki.AnkiConnectClient
import com.ankiride.bridge.anki.DesktopAnkiDispatcher
import com.ankiride.bridge.ble.DesktopZwiftBleController
import com.ankiride.bridge.ble.ZwiftGestureEngine
import com.ankiride.bridge.ble.ZwiftPacketParser
import com.ankiride.bridge.core.*
import com.ankiride.bridge.mywhoosh.DesktopMyWhooshDispatcher
import com.ankiride.bridge.mywhoosh.OpenBikeControlClient
import com.ankiride.bridge.service.DesktopActionRouter
import com.ankiride.bridge.ui.DesktopAppUi
import com.ankiride.bridge.ui.FloatingHudDesktopWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import kotlin.system.exitProcess

/**
 * Creates a standalone in-memory programmatic tray icon painter.
 * Avoids crashing on missing external image resources.
 */
fun createTrayPainter(): androidx.compose.ui.graphics.painter.Painter {
    val size = 32
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x38, 0xBD, 0xF8) // Sky Blue
        g.fillRoundRect(2, 2, size - 4, size - 4, 8, 8)
        g.color = Color(0x0F, 0x17, 0x2A) // Dark Slate
        g.font = Font("SansSerif", Font.BOLD, 19)
        g.drawString("A", 9, 23)
    } finally {
        g.dispose()
    }
    return BitmapPainter(img.toComposeImageBitmap())
}

/**
 * Headless self-test verification executed when `--self-test` CLI argument is passed.
 * Verifies:
 * 1. ZwiftPacketParser (0x23 and 0x37 packets)
 * 2. ZwiftGestureEngine (single click and 450ms long press)
 * 3. ButtonMappingStore (default actions and config persistence)
 * 4. OpenBikeControlClient (ephemeral server start & stop)
 * 5. DesktopActionRouter (gesture routing & action dispatching)
 *
 * Prints "SELF_TEST_OK" and exits with code 0 on success.
 */
fun runHeadlessSelfTest() {
    println("--- AnkiRide Bridge Desktop Headless Self-Test ---")

    // 1. Verify ZwiftPacketParser
    val zPacket = byteArrayOf(
        0x23, 0x08,
        0xFF.toByte(), 0xFE.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x0F
    )
    val parsedMaskZ = ZwiftPacketParser.parseActiveButtonMask(zPacket)
    check(parsedMaskZ == ClickButton.Z.bitmask) {
        "ZwiftPacketParser failed on Z packet: expected ${ClickButton.Z.bitmask}, got $parsedMaskZ"
    }

    val shiftUpLegacy = byteArrayOf(0x37, 0x08, 0x00)
    val shiftUpMask = ZwiftPacketParser.parseActiveButtonMask(shiftUpLegacy)
    check(shiftUpMask == ClickButton.SHIFT_UP_RIGHT.bitmask) {
        "ZwiftPacketParser failed on 0x37 Shift Up: expected ${ClickButton.SHIFT_UP_RIGHT.bitmask}, got $shiftUpMask"
    }

    val shiftDownLegacy = byteArrayOf(0x37, 0x08, 0x01)
    val shiftDownMask = ZwiftPacketParser.parseActiveButtonMask(shiftDownLegacy)
    check(shiftDownMask == ClickButton.SHIFT_DOWN_LEFT.bitmask) {
        "ZwiftPacketParser failed on 0x37 Shift Down: expected ${ClickButton.SHIFT_DOWN_LEFT.bitmask}, got $shiftDownMask"
    }
    println("✓ ZwiftPacketParser verification passed")

    // 2. Verify ZwiftGestureEngine
    var detectedEvent: ButtonGestureEvent? = null
    val gestureEngine = ZwiftGestureEngine { detectedEvent = it }

    // Test single click (press and release < 450ms)
    gestureEngine.processButtonStates(mapOf(ClickButton.Z to true), nowMs = 1000L)
    gestureEngine.processButtonStates(mapOf(ClickButton.Z to false), nowMs = 1150L)
    check(detectedEvent != null && detectedEvent?.type == PressType.SINGLE_CLICK && detectedEvent?.button == ClickButton.Z) {
        "ZwiftGestureEngine failed single click test: $detectedEvent"
    }

    // Test long press (press and hold >= 450ms)
    detectedEvent = null
    gestureEngine.processButtonStates(mapOf(ClickButton.B to true), nowMs = 2000L)
    gestureEngine.processButtonStates(mapOf(ClickButton.B to true), nowMs = 2500L)
    check(detectedEvent != null && detectedEvent?.type == PressType.LONG_PRESS && detectedEvent?.button == ClickButton.B) {
        "ZwiftGestureEngine failed long press test: $detectedEvent"
    }
    println("✓ ZwiftGestureEngine verification passed")

    // 3. Verify ButtonMappingStore
    val resolvedZ = ButtonMappingStore.resolveAction(
        OperatingMode.MODE_1_TABLET_SPLIT_SCREEN,
        ClickButton.Z,
        PressType.SINGLE_CLICK,
        emptyMap()
    )
    check(resolvedZ == MappedTargetAction.MW_SHIFT_UP) {
        "ButtonMappingStore default Z failed: expected MW_SHIFT_UP, got $resolvedZ"
    }

    val tempFile = File.createTempFile("ankiride_selftest_", ".properties")
    tempFile.deleteOnExit()
    val testConfig = AppConfiguration(
        mode = OperatingMode.MODE_5_UBUNTU_ANKICONNECT,
        ubuntuAnkiConnectHost = "127.0.0.1",
        ubuntuAnkiConnectPort = 8765,
        floatingHudPillEnabled = true
    )
    ButtonMappingStore.saveConfiguration(testConfig, tempFile)
    val loadedConfig = ButtonMappingStore.loadConfiguration(tempFile)
    check(
        loadedConfig.mode == OperatingMode.MODE_5_UBUNTU_ANKICONNECT &&
            loadedConfig.ubuntuAnkiConnectPort == 8765 &&
            loadedConfig.floatingHudPillEnabled
    ) {
        "ButtonMappingStore persistence verification failed: $loadedConfig"
    }
    tempFile.delete()
    println("✓ ButtonMappingStore verification passed")

    // 4. Verify OpenBikeControlClient start/stop
    val openBikeClient = OpenBikeControlClient()
    openBikeClient.startServer(0)
    Thread.sleep(150L)
    check(!openBikeClient.hasConnectedClients()) {
        "OpenBikeControlClient expected 0 connected clients initially"
    }
    openBikeClient.stopServer()
    println("✓ OpenBikeControlClient verification passed")

    // 5. Verify DesktopActionRouter
    var routedAnki: AnkiAction? = null
    var routedMyWhoosh: MyWhooshAction? = null
    val fakeAnki = object : AnkiActionDispatcher {
        override fun dispatch(action: AnkiAction): Boolean {
            routedAnki = action
            return true
        }
    }
    val fakeMyWhoosh = object : MyWhooshActionDispatcher {
        override suspend fun dispatch(action: MyWhooshAction, config: AppConfiguration): Boolean {
            routedMyWhoosh = action
            return true
        }
    }
    val testScope = kotlinx.coroutines.CoroutineScope(Dispatchers.Default + kotlinx.coroutines.SupervisorJob())
    val router = DesktopActionRouter(
        scope = testScope,
        configSupplier = { testConfig },
        ankiDispatcher = fakeAnki,
        myWhooshDispatcher = fakeMyWhoosh
    )
    router.routeGesture(ButtonGestureEvent(ClickButton.Z, PressType.SINGLE_CLICK))
    check(router.lastRoutedActionLabel.value == MappedTargetAction.MW_SHIFT_UP.label) {
        "DesktopActionRouter failed label check: expected '${MappedTargetAction.MW_SHIFT_UP.label}', got '${router.lastRoutedActionLabel.value}'"
    }
    println("✓ DesktopActionRouter verification passed")

    println("SELF_TEST_OK")
    exitProcess(0)
}

fun main(args: Array<String>) {
    if (args.contains("--self-test")) {
        runHeadlessSelfTest()
        return
    }

    application {
        var config by remember { mutableStateOf(ButtonMappingStore.loadConfiguration()) }
        var isBridgeRunning by remember { mutableStateOf(false) }
        var isMainWindowVisible by remember { mutableStateOf(true) }

        val scope = rememberCoroutineScope()
        val openBikeClient = remember { OpenBikeControlClient() }
        val ankiConnectClient = remember { AnkiConnectClient() }
        val desktopAnkiDispatcher = remember {
            DesktopAnkiDispatcher(configProvider = { config }, ankiConnectClient = ankiConnectClient)
        }
        val desktopMyWhooshDispatcher = remember {
            DesktopMyWhooshDispatcher(openBikeClient = openBikeClient)
        }
        val actionRouter = remember {
            DesktopActionRouter(
                scope = scope,
                configSupplier = { config },
                ankiDispatcher = desktopAnkiDispatcher,
                myWhooshDispatcher = desktopMyWhooshDispatcher
            )
        }
        val bleController = remember { DesktopZwiftBleController() }

        val bleState by bleController.connectionState.collectAsState()
        val connectedRightMac by bleController.connectedRightMac.collectAsState()
        val connectedLeftMac by bleController.connectedLeftMac.collectAsState()
        val lastActionText by actionRouter.lastRoutedActionLabel.collectAsState()

        val updateConfig: (AppConfiguration) -> Unit = { newConfig ->
            config = newConfig
            ButtonMappingStore.saveConfiguration(newConfig)
            bleController.updateConfiguration(newConfig)
        }

        val toggleBridge: () -> Unit = {
            if (isBridgeRunning) {
                bleController.disconnect()
                openBikeClient.stopServer()
                isBridgeRunning = false
                DiagnosticLogger.info(LogCategory.SYSTEM, "Bridge stopped by user")
            } else {
                openBikeClient.startServer(config.openBikeServerPort)
                bleController.startScanAndConnect(config)
                isBridgeRunning = true
                DiagnosticLogger.info(LogCategory.SYSTEM, "Bridge started by user")
            }
        }

        LaunchedEffect(Unit) {
            if (config.autoStartOnControllerWake) {
                toggleBridge()
            }
        }

        LaunchedEffect(bleController) {
            bleController.gestureEvents.collect { event ->
                actionRouter.routeGesture(event)
            }
        }

        // Programmatic Tray Icon
        val trayPainter = remember { createTrayPainter() }
        Tray(
            icon = trayPainter,
            tooltip = "AnkiRide Bridge — Zwift Click v2",
            menu = {
                Item(
                    text = if (isBridgeRunning) "Bridge: Active (Click to Stop)" else "Bridge: Stopped (Click to Start)",
                    onClick = toggleBridge
                )
                Item(
                    text = if (isMainWindowVisible) "Hide Main Window" else "Show Main Window",
                    onClick = { isMainWindowVisible = !isMainWindowVisible }
                )
                Item(
                    text = if (config.floatingHudPillEnabled) "Hide Floating HUD" else "Show Floating HUD",
                    onClick = {
                        updateConfig(config.copy(floatingHudPillEnabled = !config.floatingHudPillEnabled))
                    }
                )
                Separator()
                Item(
                    text = "Quit AnkiRide Bridge",
                    onClick = {
                        if (isBridgeRunning) {
                            bleController.disconnect()
                            openBikeClient.stopServer()
                        }
                        exitApplication()
                    }
                )
            }
        )

        // Main Desktop Window
        val mainWindowState = rememberWindowState(width = 980.dp, height = 860.dp)
        Window(
            onCloseRequest = {
                // Minimize to tray
                isMainWindowVisible = false
            },
            state = mainWindowState,
            visible = isMainWindowVisible,
            title = "AnkiRide Bridge — Zwift Click v2 Desktop (Ubuntu / macOS / Windows)"
        ) {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DesktopAppUi(
                        config = config,
                        onConfigChange = updateConfig,
                        isBridgeRunning = isBridgeRunning,
                        onToggleBridge = toggleBridge,
                        bleState = bleState,
                        connectedRightMac = connectedRightMac,
                        connectedLeftMac = connectedLeftMac,
                        hasMyWhooshClient = openBikeClient.hasConnectedClients(),
                        onSimulateGesture = { button, pressType ->
                            bleController.emitSimulatedGesture(button, pressType)
                        },
                        onTestAnkiAction = { ankiAction ->
                            scope.launch(Dispatchers.IO) {
                                desktopAnkiDispatcher.dispatch(ankiAction)
                            }
                        },
                        onTestMyWhooshAction = { mwAction ->
                            scope.launch {
                                desktopMyWhooshDispatcher.dispatch(mwAction, config)
                            }
                        }
                    )
                }
            }
        }

        // Floating Mini HUD Window
        FloatingHudDesktopWindow(
            enabled = config.floatingHudPillEnabled,
            bleState = bleState,
            mode = config.mode,
            lastActionText = lastActionText,
            onClose = {
                updateConfig(config.copy(floatingHudPillEnabled = false))
            }
        )
    }
}

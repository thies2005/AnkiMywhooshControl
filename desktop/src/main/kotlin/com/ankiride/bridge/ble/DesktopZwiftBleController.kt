package com.ankiride.bridge.ble

import com.ankiride.bridge.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.EnumMap
import java.util.concurrent.ConcurrentHashMap

class DesktopZwiftBleController(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : ClickBleController {

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _gestureEvents = MutableSharedFlow<ButtonGestureEvent>(extraBufferCapacity = 64)
    override val gestureEvents: SharedFlow<ButtonGestureEvent> = _gestureEvents

    private val _connectedRightMac = MutableStateFlow<String?>(null)
    val connectedRightMac: StateFlow<String?> = _connectedRightMac.asStateFlow()

    private val _connectedLeftMac = MutableStateFlow<String?>(null)
    val connectedLeftMac: StateFlow<String?> = _connectedLeftMac.asStateFlow()

    val podActiveMasks = ConcurrentHashMap<String, Int>()
    private val lastLoggedPayloadHex = ConcurrentHashMap<String, String>()

    private val currentButtonMap = EnumMap<ClickButton, Boolean>(ClickButton::class.java).apply {
        ClickButton.entries.forEach { put(it, false) }
    }
    private val currentButtonMapLock = Any()

    private val gestureEngine = ZwiftGestureEngine { event ->
        DiagnosticLogger.info(
            LogCategory.BLE,
            "Gesture detected: button=${event.button.name} (${event.button.displayName}), type=${event.type.name}"
        )
        scope.launch { _gestureEvents.emit(event) }
    }

    private var workerProcess: Process? = null
    private var workerJob: Job? = null
    private var tickerJob: Job? = null
    private var currentConfig = AppConfiguration()

    fun updateConfiguration(config: AppConfiguration) {
        currentConfig = config
    }

    override fun startScanAndConnect(config: AppConfiguration) {
        currentConfig = config
        disconnect()

        _connectionState.value = BleConnectionState.SCANNING
        DiagnosticLogger.info(LogCategory.BLE, "Starting Desktop BLE Controller (1-Pod Default=${config.rightPodOnlyNoZwift})")

        startTicker()
        startBleWorkerProcess(config)
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(25L)
                val snapshot = synchronized(currentButtonMapLock) {
                    EnumMap(currentButtonMap)
                }
                if (snapshot.values.any { it }) {
                    gestureEngine.processButtonStates(snapshot, System.currentTimeMillis())
                }
            }
        }
    }

    private fun startBleWorkerProcess(config: AppConfiguration) {
        workerJob?.cancel()
        workerJob = scope.launch(Dispatchers.IO) {
            try {
                val scriptFile = extractWorkerScript()
                if (scriptFile == null || !scriptFile.exists()) {
                    DiagnosticLogger.error(LogCategory.BLE, "Unable to extract or locate zwift_ble_worker.py")
                    _connectionState.value = BleConnectionState.ERROR
                    return@launch
                }

                val pythonCmd = findPythonExecutable()
                if (pythonCmd == null) {
                    DiagnosticLogger.error(LogCategory.BLE, "Python executable ('python3' or 'python') not found on system PATH")
                    _connectionState.value = BleConnectionState.ERROR
                    return@launch
                }

                val command = mutableListOf(
                    pythonCmd,
                    scriptFile.absolutePath,
                    "--right-only", config.rightPodOnlyNoZwift.toString()
                )
                if (!config.pinnedRightPodMac.isNullOrBlank()) {
                    command.add("--pinned-right")
                    command.add(config.pinnedRightPodMac)
                }
                if (!config.pinnedLeftPodMac.isNullOrBlank()) {
                    command.add("--pinned-left")
                    command.add(config.pinnedLeftPodMac)
                }

                DiagnosticLogger.info(LogCategory.BLE, "Spawning BLE worker: ${command.joinToString(" ")}")
                val pb = ProcessBuilder(command)
                pb.redirectErrorStream(false)
                val process = pb.start()
                workerProcess = process

                // Read stderr in a separate coroutine
                launch(Dispatchers.IO) {
                    try {
                        val errReader = BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8))
                        var errLine: String? = null
                        while (isActive && errReader.readLine().also { errLine = it } != null) {
                            errLine?.let {
                                if (it.isNotBlank()) {
                                    DiagnosticLogger.warn(LogCategory.BLE, "BLE worker stderr: $it")
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                // Read stdout lines
                val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
                var line: String? = null
                while (isActive && reader.readLine().also { line = it } != null) {
                    line?.let { parseWorkerLine(it.trim()) }
                }

                val exitCode = process.waitFor()
                DiagnosticLogger.warn(LogCategory.BLE, "BLE worker process terminated with exitCode=$exitCode")
                if (_connectionState.value != BleConnectionState.DISCONNECTED) {
                    _connectionState.value = BleConnectionState.DISCONNECTED
                }
            } catch (e: CancellationException) {
                // Expected on disconnect
            } catch (e: Exception) {
                DiagnosticLogger.error(LogCategory.BLE, "Exception in BLE worker supervisor: ${e.message}")
                _connectionState.value = BleConnectionState.ERROR
            }
        }
    }

    private fun parseWorkerLine(line: String) {
        if (line.isBlank()) return

        when {
            line.startsWith("STATE ") -> {
                val parts = line.removePrefix("STATE ").trim().split(" ")
                val stateName = parts.getOrNull(0) ?: return
                val mac = parts.getOrNull(1)

                when (stateName) {
                    "SCANNING" -> {
                        _connectionState.value = BleConnectionState.SCANNING
                    }
                    "CONNECTING" -> {
                        _connectionState.value = BleConnectionState.CONNECTING
                    }
                    "HANDSHAKING" -> {
                        _connectionState.value = BleConnectionState.HANDSHAKING
                    }
                    "CONNECTED_RIGHT_ONLY" -> {
                        if (mac != null) _connectedRightMac.value = mac
                        _connectionState.value = BleConnectionState.CONNECTED_RIGHT_ONLY
                        DiagnosticLogger.info(LogCategory.BLE, "Connected to Right Pod: $mac")
                    }
                    "CONNECTED_BOTH_PODS" -> {
                        if (mac != null && _connectedRightMac.value != mac) {
                            _connectedLeftMac.value = mac
                        }
                        _connectionState.value = BleConnectionState.CONNECTED_BOTH_PODS
                        DiagnosticLogger.info(LogCategory.BLE, "Connected to both pods (active: $mac)")
                    }
                    "DISCONNECTED" -> {
                        if (mac != null) {
                            podActiveMasks.remove(mac)
                            if (_connectedRightMac.value.equals(mac, ignoreCase = true)) {
                                _connectedRightMac.value = null
                            }
                            if (_connectedLeftMac.value.equals(mac, ignoreCase = true)) {
                                _connectedLeftMac.value = null
                            }
                            updateCurrentButtonsAndProcess(System.currentTimeMillis())
                        }
                        if (_connectedRightMac.value != null) {
                            _connectionState.value = BleConnectionState.CONNECTED_RIGHT_ONLY
                        } else {
                            _connectionState.value = BleConnectionState.DISCONNECTED
                        }
                    }
                    "ERROR" -> {
                        _connectionState.value = BleConnectionState.ERROR
                        DiagnosticLogger.error(LogCategory.BLE, "BLE worker error: ${parts.drop(1).joinToString(" ")}")
                    }
                }
            }

            line.startsWith("PACKET ") -> {
                val parts = line.removePrefix("PACKET ").trim().split(" ")
                if (parts.size >= 2) {
                    val mac = parts[0]
                    val hex = parts[1]
                    val bytes = hexStringToByteArray(hex)
                    if (bytes.isNotEmpty()) {
                        handleRawNotification(mac, bytes)
                    }
                }
            }

            line.startsWith("LOG ") -> {
                val parts = line.removePrefix("LOG ").trim().split(" ", limit = 2)
                val level = parts.getOrNull(0)?.uppercase() ?: "INFO"
                val msg = parts.getOrNull(1) ?: ""
                when (level) {
                    "WARN" -> DiagnosticLogger.warn(LogCategory.BLE, "BLE worker: $msg")
                    "ERROR" -> DiagnosticLogger.error(LogCategory.BLE, "BLE worker: $msg")
                    else -> DiagnosticLogger.info(LogCategory.BLE, "BLE worker: $msg")
                }
            }

            else -> {
                DiagnosticLogger.info(LogCategory.BLE, "BLE worker stdout: $line")
            }
        }
    }

    fun handleRawNotification(mac: String, payload: ByteArray, nowMs: Long = System.currentTimeMillis()) {
        if (payload.isEmpty()) return
        val msgId = payload[0]
        // Suppress periodic idle keep-alive heartbeats (0x15, 0x19)
        if (msgId == 0x15.toByte() || msgId == 0x19.toByte()) return

        val mask = ZwiftPacketParser.parseActiveButtonMask(payload)
        if (mask == null) {
            val hex = payload.joinToString(" ") { "%02X".format(it) }
            DiagnosticLogger.info(LogCategory.PACKET, "Non-0x23 packet from $mac (msgId=0x${"%02X".format(msgId)}): [$hex]")
            return
        }

        val prevMask = podActiveMasks.put(mac, mask) ?: 0
        val hex = payload.joinToString(" ") { "%02X".format(it) }
        if (mask != 0 || prevMask != 0 || lastLoggedPayloadHex.put(mac, hex) != hex) {
            if (mask != 0) {
                val matchedNames = ClickButton.entries.filter { (mask and it.bitmask) != 0 }.joinToString(",") { it.name }
                DiagnosticLogger.info(
                    LogCategory.PACKET,
                    "0x23 packet from $mac: [$hex] -> activeMask=0x${"%05X".format(mask)} (${matchedNames.ifEmpty { "UNMAPPED_BIT" }})"
                )
            }
        }

        updateCurrentButtonsAndProcess(nowMs)
    }

    private fun updateCurrentButtonsAndProcess(nowMs: Long = System.currentTimeMillis()) {
        val combinedMask = podActiveMasks.values.fold(0) { acc, m -> acc or m }
        val combinedMap = EnumMap<ClickButton, Boolean>(ClickButton::class.java)
        ClickButton.entries.forEach { button ->
            combinedMap[button] = (combinedMask and button.bitmask) != 0
        }
        synchronized(currentButtonMapLock) {
            currentButtonMap.clear()
            currentButtonMap.putAll(combinedMap)
        }
        gestureEngine.processButtonStates(combinedMap, nowMs)
    }

    fun emitSimulatedGesture(button: ClickButton, pressType: PressType) {
        val event = ButtonGestureEvent(button, pressType, System.currentTimeMillis())
        DiagnosticLogger.info(
            LogCategory.BLE,
            "Simulated gesture emitted: ${button.displayName} (${pressType.name})"
        )
        scope.launch { _gestureEvents.emit(event) }
    }

    override fun disconnect() {
        tickerJob?.cancel()
        tickerJob = null

        workerJob?.cancel()
        workerJob = null

        workerProcess?.let { proc ->
            try {
                proc.destroy()
                if (!proc.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    proc.destroyForcibly()
                }
            } catch (_: Exception) {
            }
        }
        workerProcess = null

        podActiveMasks.clear()
        lastLoggedPayloadHex.clear()
        synchronized(currentButtonMapLock) {
            ClickButton.entries.forEach { currentButtonMap[it] = false }
        }
        _connectedRightMac.value = null
        _connectedLeftMac.value = null
        _connectionState.value = BleConnectionState.DISCONNECTED
    }

    private fun extractWorkerScript(): File? {
        val resourceStream = javaClass.getResourceAsStream("/ble/zwift_ble_worker.py")
        if (resourceStream != null) {
            val target = File(System.getProperty("java.io.tmpdir"), "ankiride_zwift_ble_worker.py")
            FileOutputStream(target).use { fos ->
                resourceStream.copyTo(fos)
            }
            target.setExecutable(true)
            return target
        }

        // Direct filesystem search fallback for development runs
        val devPaths = listOf(
            File("desktop/src/main/resources/ble/zwift_ble_worker.py"),
            File("src/main/resources/ble/zwift_ble_worker.py"),
            File("resources/ble/zwift_ble_worker.py")
        )
        return devPaths.firstOrNull { it.exists() && it.isFile }
    }

    private fun findPythonExecutable(): String? {
        val candidates = if (System.getProperty("os.name").lowercase().contains("win")) {
            listOf("python.exe", "python3.exe", "python")
        } else {
            listOf("python3", "python", "/usr/bin/python3", "/usr/local/bin/python3")
        }

        for (cmd in candidates) {
            try {
                val pb = ProcessBuilder(cmd, "--version")
                val proc = pb.start()
                if (proc.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS) && proc.exitValue() == 0) {
                    return cmd
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val clean = s.replace(" ", "").trim()
        val len = clean.length
        if (len == 0 || len % 2 != 0) return ByteArray(0)
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            val high = Character.digit(clean[i], 16)
            val low = Character.digit(clean[i + 1], 16)
            if (high == -1 || low == -1) return ByteArray(0)
            data[i / 2] = ((high shl 4) + low).toByte()
            i += 2
        }
        return data
    }
}

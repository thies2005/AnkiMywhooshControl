package com.ankiride.bridge.anki

import android.accessibilityservice.AccessibilityService
import com.ankiride.bridge.core.AnkiAction
import com.ankiride.bridge.core.AnkiActionDispatcher
import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.OperatingMode
import com.ankiride.bridge.core.PresetStore
import com.ankiride.bridge.core.AnkiTarget
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class AnkiActionDispatcherImpl(
    private val bridge: AnkiAccessibilityBridge = AnkiAccessibilityBridge(),
    private val ankiConnectClient: AnkiConnectClient = AnkiConnectClient(),
    private val configSupplier: () -> AppConfiguration = { AppConfiguration() },
    private val serviceProvider: () -> AccessibilityService?
) : AnkiActionDispatcher {

    val ioExecutor: ThreadPoolExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue()
    )

    override fun dispatch(action: AnkiAction): Boolean {
        val config = configSupplier()
        if ((PresetStore.active(config)?.ankiTarget ?: if (config.mode == OperatingMode.MODE_5_UBUNTU_ANKICONNECT) AnkiTarget.ANKICONNECT else AnkiTarget.ANKIDROID) == AnkiTarget.ANKICONNECT) {
            ioExecutor.execute {
                ankiConnectClient.dispatchAnkiConnectAction(
                    host = config.ubuntuAnkiConnectHost,
                    port = config.ubuntuAnkiConnectPort,
                    action = action
                )
            }
            return true
        }

        val service = serviceProvider() ?: return false
        return bridge.performAnkiAction(service, action)
    }
}

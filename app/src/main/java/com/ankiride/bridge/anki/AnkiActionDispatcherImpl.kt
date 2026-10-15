package com.ankiride.bridge.anki

import android.accessibilityservice.AccessibilityService
import com.ankiride.bridge.core.AnkiAction
import com.ankiride.bridge.core.AnkiActionDispatcher
import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.OperatingMode
import java.util.concurrent.Executors

class AnkiActionDispatcherImpl(
    private val bridge: AnkiAccessibilityBridge = AnkiAccessibilityBridge(),
    private val ankiConnectClient: AnkiConnectClient = AnkiConnectClient(),
    private val configSupplier: () -> AppConfiguration = { AppConfiguration() },
    private val serviceProvider: () -> AccessibilityService?
) : AnkiActionDispatcher {

    private val ioExecutor = Executors.newSingleThreadExecutor()

    override fun dispatch(action: AnkiAction): Boolean {
        val config = configSupplier()
        if (config.mode == OperatingMode.MODE_5_UBUNTU_ANKICONNECT) {
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

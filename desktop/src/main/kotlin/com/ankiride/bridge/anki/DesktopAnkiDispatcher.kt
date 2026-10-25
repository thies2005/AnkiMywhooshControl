package com.ankiride.bridge.anki

import com.ankiride.bridge.core.AnkiAction
import com.ankiride.bridge.core.AnkiActionDispatcher
import com.ankiride.bridge.core.AppConfiguration

class DesktopAnkiDispatcher(
    private val configProvider: () -> AppConfiguration,
    private val ankiConnectClient: AnkiConnectClient = AnkiConnectClient()
) : AnkiActionDispatcher {

    override fun dispatch(action: AnkiAction): Boolean {
        val config = configProvider()
        val rawHost = config.ubuntuAnkiConnectHost.trim()
        val host = if (rawHost.isBlank()) "127.0.0.1" else rawHost
        val port = config.ubuntuAnkiConnectPort
        return ankiConnectClient.dispatchAnkiConnectAction(host, port, action)
    }
}

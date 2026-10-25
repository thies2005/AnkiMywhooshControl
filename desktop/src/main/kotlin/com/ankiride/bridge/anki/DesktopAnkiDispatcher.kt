package com.ankiride.bridge.anki

import com.ankiride.bridge.core.AnkiAction
import com.ankiride.bridge.core.AnkiActionDispatcher
import com.ankiride.bridge.core.AppConfiguration
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class DesktopAnkiDispatcher(
    private val configProvider: () -> AppConfiguration,
    private val ankiConnectClient: AnkiConnectClient = AnkiConnectClient(),
    val executor: ThreadPoolExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(1),
        ThreadPoolExecutor.DiscardOldestPolicy()
    )
) : AnkiActionDispatcher {

    fun dispatchAsync(action: AnkiAction) {
        executor.execute {
            dispatch(action)
        }
    }

    override fun dispatch(action: AnkiAction): Boolean {
        val config = configProvider()
        val rawHost = config.ubuntuAnkiConnectHost.trim()
        val host = if (rawHost.isBlank()) "127.0.0.1" else rawHost
        val port = config.ubuntuAnkiConnectPort
        return ankiConnectClient.dispatchAnkiConnectAction(host, port, action)
    }
}

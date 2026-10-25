package com.ankiride.bridge.mywhoosh

import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.DiagnosticLogger
import com.ankiride.bridge.core.LogCategory
import com.ankiride.bridge.core.MyWhooshAction
import com.ankiride.bridge.core.MyWhooshActionDispatcher

class DesktopMyWhooshDispatcher(
    private val openBikeClient: OpenBikeControlClient
) : MyWhooshActionDispatcher {

    override suspend fun dispatch(action: MyWhooshAction, config: AppConfiguration): Boolean {
        // 1. If OpenBikeControl has connected clients (local or over LAN), dispatch via TCP protocol
        if (openBikeClient.hasConnectedClients()) {
            val delivered = openBikeClient.sendAction("127.0.0.1", config.openBikeServerPort, action)
            if (delivered) {
                return true
            }
        }

        // 2. Otherwise if fallback is enabled when no client is connected, try keyboard shortcut first, then screen click
        if (config.useMyShiftTouchFallbackWhenNoClient) {
            val keyboardOk = DesktopMyWhooshRobot.performKeyboardShiftOrAction(action)
            if (keyboardOk) {
                return true
            }

            val clickOk = DesktopMyWhooshRobot.performScreenClickFallback(action, config)
            if (clickOk) {
                return true
            }

            DiagnosticLogger.warn(LogCategory.MYWHOOSH, "Desktop Robot fallback failed for $action")
        }

        return false
    }
}

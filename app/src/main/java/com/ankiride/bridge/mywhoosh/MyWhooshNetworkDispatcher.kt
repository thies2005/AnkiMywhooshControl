package com.ankiride.bridge.mywhoosh

import android.accessibilityservice.AccessibilityService
import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.MyWhooshAction
import com.ankiride.bridge.core.MyWhooshActionDispatcher
import com.ankiride.bridge.core.OperatingMode

class MyWhooshNetworkDispatcher(
    private val client: OpenBikeControlClient = OpenBikeControlClient(),
    private val serviceProvider: () -> AccessibilityService? = { null }
) : MyWhooshActionDispatcher {

    override suspend fun dispatch(action: MyWhooshAction, config: AppConfiguration): Boolean {
        // Check if local touch fallback is enabled for shifting on the Tablet
        val isTabletLocalMode = config.mode == OperatingMode.MODE_1_TABLET_BOTH || config.mode == OperatingMode.MODE_4_MYWHOOSH_ONLY
        val isShiftAction = action == MyWhooshAction.SHIFT_UP || action == MyWhooshAction.SHIFT_DOWN

        if (config.useAccessibilityTapFallbackForMyWhoosh && isTabletLocalMode && isShiftAction) {
            val service = serviceProvider()
            if (service != null) {
                val tapped = MyShiftGestureFallback.performShiftTap(service, action, config)
                if (tapped) return true
            }
        }

        // Default: OpenBikeControl protocol dispatch
        return client.sendAction(
            host = config.targetMyWhooshHost,
            port = config.targetMyWhooshPort,
            action = action
        )
    }

    fun close() {
        client.close()
    }
}

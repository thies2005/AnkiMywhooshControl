package com.ankiride.bridge.mywhoosh

import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.DiagnosticLogger
import com.ankiride.bridge.core.LogCategory
import com.ankiride.bridge.core.MyWhooshAction
import com.ankiride.bridge.core.MyWhooshActionDispatcher
import com.ankiride.bridge.core.OperatingMode
import com.ankiride.bridge.service.BridgeAccessibilityService

class MyWhooshNetworkDispatcherImpl(
    private val client: OpenBikeControlClient,
    private val accessibilityServiceSupplier: () -> BridgeAccessibilityService?
) : MyWhooshActionDispatcher {

    override suspend fun dispatch(action: MyWhooshAction, config: AppConfiguration): Boolean {
        var deliveredNetwork = false

        // 1. Send official binary OpenBikeControl packet (0x01) to connected MyWhoosh client (Tablet or Pixel 10)
        if (client.hasConnectedClients()) {
            deliveredNetwork = client.sendAction("127.0.0.1", config.openBikeServerPort, action)
        } else {
            DiagnosticLogger.warn(
                LogCategory.MYWHOOSH,
                "No OpenBikeControl TCP client connected yet for $action (tap OpenBikeControl icon in MyWhoosh Connection Screen)"
            )
        }

        // 2. Also perform on-screen MyShift tap in Tablet Modes if enabled (for SHIFT_UP / SHIFT_DOWN)
        val isTabletMyWhoosh = config.mode == OperatingMode.MODE_1_TABLET_SPLIT_SCREEN ||
            config.mode == OperatingMode.MODE_4_MYWHOOSH_ONLY

        var deliveredTouch = false
        if (isTabletMyWhoosh && config.useMyShiftTouchFallbackWhenNoClient &&
            (action == MyWhooshAction.SHIFT_UP || action == MyWhooshAction.SHIFT_DOWN)
        ) {
            val service = accessibilityServiceSupplier()
            if (service != null) {
                deliveredTouch = MyShiftGestureFallback.performTouchAction(service, action, config)
            }
        }

        return deliveredNetwork || deliveredTouch
    }
}

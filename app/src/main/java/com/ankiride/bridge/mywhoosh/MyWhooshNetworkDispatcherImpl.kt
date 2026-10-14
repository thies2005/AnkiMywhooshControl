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
        val isTabletMyWhoosh = config.mode == OperatingMode.MODE_1_TABLET_SPLIT_SCREEN ||
            config.mode == OperatingMode.MODE_4_MYWHOOSH_ONLY

        // 1. Fire immediate 0ms-latency on-screen MyShift touch tap first in Tablet Modes
        var deliveredTouch = false
        if (isTabletMyWhoosh && config.useMyShiftTouchFallbackWhenNoClient &&
            (action == MyWhooshAction.SHIFT_UP || action == MyWhooshAction.SHIFT_DOWN)
        ) {
            val service = accessibilityServiceSupplier()
            if (service != null) {
                deliveredTouch = MyShiftGestureFallback.performTouchAction(service, action, config)
            } else {
                DiagnosticLogger.warn(LogCategory.MYWHOOSH, "AccessibilityService not active for MyShift touch tap")
            }
        }

        // 2. Also send official binary OpenBikeControl packet (0x01) if a client is connected
        var deliveredNetwork = false
        if (client.hasConnectedClients()) {
            deliveredNetwork = client.sendAction("127.0.0.1", config.openBikeServerPort, action)
        }

        return deliveredTouch || deliveredNetwork
    }
}

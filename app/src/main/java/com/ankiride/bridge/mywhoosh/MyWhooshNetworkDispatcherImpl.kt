package com.ankiride.bridge.mywhoosh

import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.MyWhooshAction
import com.ankiride.bridge.core.MyWhooshActionDispatcher
import com.ankiride.bridge.core.OperatingMode
import com.ankiride.bridge.service.BridgeAccessibilityService

class MyWhooshNetworkDispatcherImpl(
    private val client: OpenBikeControlClient,
    private val accessibilityServiceSupplier: () -> BridgeAccessibilityService?
) : MyWhooshActionDispatcher {

    override suspend fun dispatch(action: MyWhooshAction, config: AppConfiguration): Boolean {
        // 1. Primary: Send official binary OpenBikeControl packet (0x01) to connected MyWhoosh client (Tablet or Pixel 10)
        if (client.hasConnectedClients()) {
            if (client.sendAction("127.0.0.1", config.openBikeServerPort, action)) {
                return true
            }
        }

        // 2. Fallback: If MyWhoosh is on the Tablet and OpenBikeControl isn't paired yet, tap MyShift arrows directly
        val isTabletMyWhoosh = config.mode == OperatingMode.MODE_1_TABLET_SPLIT_SCREEN ||
            config.mode == OperatingMode.MODE_4_MYWHOOSH_ONLY

        if (isTabletMyWhoosh && config.useMyShiftTouchFallbackWhenNoClient) {
            val service = accessibilityServiceSupplier()
            if (service != null && MyShiftGestureFallback.performTouchAction(service, action, config)) {
                return true
            }
        }
        return false
    }
}

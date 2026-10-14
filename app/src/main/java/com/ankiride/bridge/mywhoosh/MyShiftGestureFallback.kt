package com.ankiride.bridge.mywhoosh

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.MyWhooshAction

object MyShiftGestureFallback {
    private const val TAG = "MyShiftGestureFallback"
    const val MYWHOOSH_PACKAGE = "com.mywhoosh.whooshgame"
    const val TAP_DURATION_MS = 15L

    fun performShiftTap(
        service: AccessibilityService,
        action: MyWhooshAction,
        config: AppConfiguration
    ): Boolean {
        if (action != MyWhooshAction.SHIFT_UP && action != MyWhooshAction.SHIFT_DOWN) {
            return false
        }

        val myWhooshWindow = service.windows?.firstOrNull {
            it.root?.packageName?.toString() == MYWHOOSH_PACKAGE
        } ?: return false

        val bounds = Rect()
        myWhooshWindow.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return false

        val (normX, normY) = if (action == MyWhooshAction.SHIFT_UP) {
            config.myShiftUpNormalizedX to config.myShiftUpNormalizedY
        } else {
            config.myShiftDownNormalizedX to config.myShiftDownNormalizedY
        }

        val tapX = bounds.left + (bounds.width() * normX)
        val tapY = bounds.top + (bounds.height() * normY)

        val path = Path().apply {
            moveTo(tapX, tapY)
            lineTo(tapX, tapY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return try {
            service.dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch gesture tap for MyShift", e)
            false
        }
    }
}

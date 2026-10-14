package com.ankiride.bridge.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class BridgeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BridgeA11yService"
        var activeInstance: BridgeAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "BridgeAccessibilityService connected")
        activeInstance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Window events handled directly on demand via windows inspection
    }

    override fun onInterrupt() {
        Log.w(TAG, "BridgeAccessibilityService interrupted")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Security Gate SG-2 check: reject all external onStartCommand triggers
        Log.w(TAG, "Rejected external onStartCommand trigger")
        return START_NOT_STICKY
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (activeInstance == this) {
            activeInstance = null
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (activeInstance == this) {
            activeInstance = null
        }
        super.onDestroy()
    }
}

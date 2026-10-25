package com.ankiride.bridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.ankiride.bridge.core.BleConnectionState
import com.ankiride.bridge.core.OperatingMode

/**
 * Cross-platform Floating Mini HUD Pill for Compose Desktop (Ubuntu, macOS, Windows).
 * Renders an always-on-top, undecorated, non-focusable window that floats over MyWhoosh
 * and Anki without stealing keyboard or mouse focus.
 */
@Composable
fun FloatingHudDesktopWindow(
    enabled: Boolean,
    bleState: BleConnectionState,
    mode: OperatingMode,
    lastActionText: String,
    onClose: () -> Unit
) {
    if (!enabled) return

    val windowState = rememberWindowState(size = DpSize(340.dp, 50.dp))
    Window(
        onCloseRequest = onClose,
        state = windowState,
        title = "AnkiRide HUD",
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        focusable = false, // Never steals focus from MyWhoosh or Anki!
        resizable = false
    ) {
        val dot = when (bleState) {
            BleConnectionState.CONNECTED_RIGHT_ONLY,
            BleConnectionState.CONNECTED_BOTH_PODS -> "🟢"
            BleConnectionState.SCANNING,
            BleConnectionState.CONNECTING,
            BleConnectionState.HANDSHAKING -> "🟡"
            else -> "🔴"
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xDD1E293B), RoundedCornerShape(25.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$dot ${mode.shortTitle}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = lastActionText.ifBlank { "Ready" },
                color = Color(0xFF38BDF8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

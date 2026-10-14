#!/usr/bin/env bash
set -euo pipefail

echo "=== [RG-0] Checking Core Contracts (ModeButtonBindingKey, Z=0x0080) & ButtonMappingStore ==="
test -f app/src/main/java/com/ankiride/bridge/core/DiagnosticLogger.kt
test -f app/src/main/java/com/ankiride/bridge/core/ButtonMappingStore.kt
grep -q "ModeButtonBindingKey" app/src/main/java/com/ankiride/bridge/core/Contracts.kt
grep -q "Z(0x0080" app/src/main/java/com/ankiride/bridge/core/Contracts.kt
grep -q "MappedTargetAction" app/src/main/java/com/ankiride/bridge/core/Contracts.kt
grep -q "formatForClipboard" app/src/main/java/com/ankiride/bridge/core/DiagnosticLogger.kt

echo "=== [RG-1] Checking BLE Bounds, Handshake, Debounce & Cross-Pod Merging ==="
grep -q "MAX_VARINT_BYTES = 5" app/src/main/java/com/ankiride/bridge/ble/ZwiftPacketParser.kt
grep -q "MAX_PACKET_SIZE = 64" app/src/main/java/com/ankiride/bridge/ble/ZwiftPacketParser.kt
grep -q "DEBOUNCE_WINDOW_MS" app/src/main/java/com/ankiride/bridge/ble/ZwiftGestureEngine.kt
grep -q "podActiveMasks" app/src/main/java/com/ankiride/bridge/ble/ZwiftBleController.kt
grep -q "DiagnosticLogger" app/src/main/java/com/ankiride/bridge/ble/ZwiftBleController.kt

echo "=== [RG-2] Checking Accessibility Package Pinning, Password Guard & Window Inspector ==="
grep -q 'android:packageNames="com.ichi2.anki,com.mywhoosh.whooshgame"' app/src/main/res/xml/accessibility_service_config.xml
grep -q "isPassword" app/src/main/java/com/ankiride/bridge/anki/AnkiAccessibilityBridge.kt
grep -q "dumpInteractiveWindowsToLog" app/src/main/java/com/ankiride/bridge/anki/AnkiAccessibilityBridge.kt

echo "=== [RG-3] Checking Dual-Stack Normalization, Overlay Calibrator & Touch Dispatch ==="
grep -q 'cleartextTrafficPermitted="false"' app/src/main/res/xml/network_security_config.xml
grep -q "normalizeIpv4MappedHost" app/src/main/java/com/ankiride/bridge/mywhoosh/LanAddressValidator.kt
grep -q "isSiteLocalAddress" app/src/main/java/com/ankiride/bridge/mywhoosh/LanAddressValidator.kt
grep -q "tcpNoDelay = true" app/src/main/java/com/ankiride/bridge/mywhoosh/OpenBikeControlClient.kt
grep -q "MSG_APP_INFO" app/src/main/java/com/ankiride/bridge/mywhoosh/OpenBikeControlClient.kt
grep -q "TYPE_ACCESSIBILITY_OVERLAY" app/src/main/java/com/ankiride/bridge/mywhoosh/MyShiftOverlayCalibrator.kt

echo "=== [RG-4] Checking ColorCodedButtonCard, ON_RESUME Refresh & Diagnostic Controls ==="
grep -q "ColorCodedButtonCard" app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt
grep -q "ON_RESUME" app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt
grep -q "Copy Logs" app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt
grep -q "Clear Logs" app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt
grep -q "triggerTestShift" app/src/main/java/com/ankiride/bridge/service/BridgeForegroundService.kt

echo "=== [RG-5] Running Full Unit Test Suite ==="
./gradlew testDebugUnitTest --no-daemon

echo "ALL REVIEW & SECURITY GATES (RG-0 .. RG-5) PASSED."

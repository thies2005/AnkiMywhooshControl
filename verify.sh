#!/usr/bin/env bash
set -euo pipefail

echo "=== [RG-0] Checking Core Contracts & DiagnosticLogger ==="
test -f app/src/main/java/com/ankiride/bridge/core/DiagnosticLogger.kt
grep -q "MappedTargetAction" app/src/main/java/com/ankiride/bridge/core/Contracts.kt
grep -q "formatForClipboard" app/src/main/java/com/ankiride/bridge/core/DiagnosticLogger.kt

echo "=== [RG-1] Checking BLE Bounds, Handshake & Diagnostic Logging ==="
grep -q "MAX_VARINT_BYTES = 5" app/src/main/java/com/ankiride/bridge/ble/ZwiftPacketParser.kt
grep -q "MAX_PACKET_SIZE = 64" app/src/main/java/com/ankiride/bridge/ble/ZwiftPacketParser.kt
grep -q "DiagnosticLogger" app/src/main/java/com/ankiride/bridge/ble/ZwiftBleController.kt

echo "=== [RG-2] Checking Accessibility Package Pinning, Password Guard & Window Inspector ==="
grep -q 'android:packageNames="com.ichi2.anki,com.mywhoosh.whooshgame"' app/src/main/res/xml/accessibility_service_config.xml
grep -q "isPassword" app/src/main/java/com/ankiride/bridge/anki/AnkiAccessibilityBridge.kt
grep -q "dumpInteractiveWindowsToLog" app/src/main/java/com/ankiride/bridge/anki/AnkiAccessibilityBridge.kt

echo "=== [RG-3] Checking Dual-Stack IPv4-Mapped Normalization & Network Security Config ==="
grep -q 'cleartextTrafficPermitted="false"' app/src/main/res/xml/network_security_config.xml
grep -q "normalizeIpv4MappedHost" app/src/main/java/com/ankiride/bridge/mywhoosh/LanAddressValidator.kt
grep -q "isSiteLocalAddress" app/src/main/java/com/ankiride/bridge/mywhoosh/LanAddressValidator.kt

echo "=== [RG-4] Checking ON_RESUME Permission Auto-Refresh, Copy/Clear Log UI & Custom Mapping ==="
grep -q "ON_RESUME" app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt
grep -q "Copy Logs" app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt
grep -q "Clear Logs" app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt
grep -q "CustomButtonMappingCard" app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt

echo "=== [RG-5] Running Full Unit Test Suite ==="
./gradlew testDebugUnitTest --no-daemon

echo "ALL REVIEW & SECURITY GATES (RG-0 .. RG-5) PASSED."

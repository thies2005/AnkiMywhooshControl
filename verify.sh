#!/usr/bin/env bash
set -euo pipefail

echo "=== [RG-0] Checking Core Contracts (Defaults OFF for HUD/Haptic/Audio + Mode 5) ==="
grep -q "floatingHudPillEnabled: Boolean = false" app/src/main/java/com/ankiride/bridge/core/Contracts.kt
grep -q "hapticFeedbackEnabled: Boolean = false" app/src/main/java/com/ankiride/bridge/core/Contracts.kt
grep -q "audioFeedbackEnabled: Boolean = false" app/src/main/java/com/ankiride/bridge/core/Contracts.kt
grep -q "MODE_5_UBUNTU_ANKICONNECT" app/src/main/java/com/ankiride/bridge/core/Contracts.kt

echo "=== [RG-1] Checking BLE Parser Bounds & Gesture Engine Long-Press Release Fix ==="
grep -q "MAX_VARINT_BYTES = 5" app/src/main/java/com/ankiride/bridge/ble/ZwiftPacketParser.kt
grep -q "DEBOUNCE_WINDOW_MS = 120L" app/src/main/java/com/ankiride/bridge/ble/ZwiftGestureEngine.kt

echo "=== [RG-2] Checking AnkiAccessibilityBridge & Ubuntu AnkiConnectClient ==="
test -f app/src/main/java/com/ankiride/bridge/anki/AnkiConnectClient.kt
grep -q "LanAddressValidator.isLocalOrLanAddress" app/src/main/java/com/ankiride/bridge/anki/AnkiConnectClient.kt
grep -q "guiAnswerCard" app/src/main/java/com/ankiride/bridge/anki/AnkiConnectClient.kt

echo "=== [RG-3] Checking Profile-Keyed MyShiftOverlayCalibrator & Network Security Config ==="
grep -q "profilePrefix" app/src/main/java/com/ankiride/bridge/mywhoosh/MyShiftOverlayCalibrator.kt
grep -q "TAP_DURATION_MS = 75L" app/src/main/java/com/ankiride/bridge/mywhoosh/MyShiftGestureFallback.kt
grep -q 'cleartextTrafficPermitted="false"' app/src/main/res/xml/network_security_config.xml

echo "=== [RG-4 & RG-5] Checking FloatingHudPill, FeedbackCueManager & Per-Mode UI ==="
test -f app/src/main/java/com/ankiride/bridge/ui/FloatingHudPill.kt
test -f app/src/main/java/com/ankiride/bridge/service/FeedbackCueManager.kt
grep -q "ColorCodedButtonCard" app/src/main/java/com/ankiride/bridge/ui/MainActivity.kt

echo "=== [RG-6] Running Full Unit Test Suite ==="
./gradlew testDebugUnitTest --no-daemon

echo "ALL REVIEW & SECURITY GATES (RG-0 .. RG-6) PASSED."

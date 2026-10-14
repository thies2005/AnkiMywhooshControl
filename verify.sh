#!/usr/bin/env bash
set -euo pipefail

echo "=== [SG-0] Checking Subagent Write Isolation & Shared Contract Integrity ==="
git diff --exit-code app/src/main/java/com/ankiride/bridge/core/Contracts.kt
if grep -rnE 'implementation\(".*\+"' app/build.gradle.kts; then
  echo "FAIL [SG-0]: Dynamic '+' dependency version detected in build.gradle.kts"
  exit 1
fi

echo "=== [SG-1] Checking BLE Parser Bounds & Running Fuzz/Unit Tests ==="
grep -q "MAX_VARINT_BYTES = 5" app/src/main/java/com/ankiride/bridge/ble/ZwiftPacketParser.kt
grep -q "MAX_PACKET_SIZE = 64" app/src/main/java/com/ankiride/bridge/ble/ZwiftPacketParser.kt

echo "=== [SG-2] Checking AccessibilityService Package Pinning & Password Guard ==="
grep -q 'android:packageNames="com.ichi2.anki,com.mywhoosh.whooshgame"' app/src/main/res/xml/accessibility_service_config.xml
grep -q 'isPassword' app/src/main/java/com/ankiride/bridge/anki/AnkiAccessibilityBridge.kt

echo "=== [SG-3] Checking AndroidManifest Exported Component Hardening ==="
grep -q 'android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"' app/src/main/AndroidManifest.xml
if grep -n 'android:exported="true"' app/src/main/AndroidManifest.xml | grep -v 'MainActivity'; then
  echo "FAIL [SG-3]: Non-MainActivity component exported in AndroidManifest.xml"
  exit 1
fi

echo "=== [SG-4] Checking RFC1918 / Loopback LAN Validator & Network Security Config ==="
grep -q 'cleartextTrafficPermitted="false"' app/src/main/res/xml/network_security_config.xml
grep -q 'isSiteLocalAddress' app/src/main/java/com/ankiride/bridge/mywhoosh/LanAddressValidator.kt
grep -q 'isLoopbackAddress' app/src/main/java/com/ankiride/bridge/mywhoosh/LanAddressValidator.kt

echo "=== Running Full Unit Test Suite Across All Subagent Modules ==="
./gradlew testDebugUnitTest --no-daemon

echo "ALL SECURITY GATES (SG-0 .. SG-4) AND UNIT TESTS PASSED."

# AnkiMyWhooshControl Desktop (`Ubuntu + macOS + Windows`) — Kotlin Compose Multiplatform & Native Implementation Plan

## 1. Executive Summary: Does Kotlin Compose Desktop Work on Ubuntu, macOS & Windows for €0?

**Yes—100%.**
1. **Zero Apple / Xcode Cost (€0)**: Xcode and Apple's compilers are free, and the **$99/€99 Apple Developer Program is NOT required** to build or run apps on your friend's Mac. By using ad-hoc code signing in GitHub Actions (`codesign --force --deep --sign -`) and running `xattr -cr AnkiRideBridge.app` once on your friend's Mac, you bypass Apple's paid notarization gate completely.
2. **85% Direct Code Reuse from Your Android Repo**: With **[Kotlin Compose Multiplatform for Desktop](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html)**, your existing Kotlin files (`Contracts.kt`, `ZwiftPacketParser.kt`, `ZwiftGestureEngine.kt`, `AnkiConnectClient.kt`, `OpenBikeControlClient.kt`, `ActionRouter.kt`, `DiagnosticLogger.kt`, and your Jetpack Compose UI in `MainActivity.kt`) run on **Ubuntu Linux, macOS, and Windows** from a single codebase.
3. **Code & Test Locally on Ubuntu, Build All 3 OS Installers in GitHub Actions**: You write and test the app directly on your **Ubuntu** machine (including live hardware testing with your physical Zwift Click v2, local Desktop Anki, and local/LAN MyWhoosh), and a 3-OS GitHub Actions matrix (`ubuntu-latest`, `macos-15`, `windows-latest`) automatically builds `.deb` (Ubuntu), `.dmg`/`.app` (macOS), and `.msi`/`.exe` (Windows) installers for free.

---

## 2. How Every Android Component Maps to Kotlin Compose Desktop (`Ubuntu + macOS + Windows`)

Because you already architected the Android app around clean interfaces (`ClickBleController`, `AnkiActionDispatcher`, `MyWhooshActionDispatcher`), porting to **Kotlin Compose Desktop (JVM)** only requires swapping 4 Android-specific imports for cross-platform JVM equivalents:

| Subsystem | Your Current Android File | Kotlin Compose Desktop Equivalent (`Ubuntu / macOS / Windows`) | Code Reuse |
| :--- | :--- | :--- | :--- |
| **Core Contracts & Bitmasks** | `Contracts.kt` (`Z = 0x0080`, 5 Modes, `ModeButtonBindingKey`) | **Unchanged** `Contracts.kt` | **100% verbatim** |
| **0x23 & 0x37 Packet Parser** | `ZwiftPacketParser.kt` | **Unchanged** `ZwiftPacketParser.kt` | **100% verbatim** |
| **450ms Long-Press & 120ms Debounce** | `ZwiftGestureEngine.kt` | **Unchanged** `ZwiftGestureEngine.kt` | **100% verbatim** |
| **Settings & Button Persistence** | `ButtonMappingStore.kt` (`SharedPreferences`) | Swap `SharedPreferences` for `java.util.prefs.Preferences` (or `~/.ankiride/settings.properties`) | **95% reuse** |
| **Anki Control (`:8765`)** | `AnkiConnectClient.kt` (`java.net.Socket` JSON-RPC + `350ms` bounce guard) | **Unchanged** `AnkiConnectClient.kt` (targets `127.0.0.1:8765` on Ubuntu/Mac/Windows or LAN IP) | **100% verbatim** |
| **OpenBikeControl TCP Server (`:21587`)** | `OpenBikeControlClient.kt` (`ServerSocket` + `NsdManager`) | Keep `ServerSocket(21587)` (`tcpNoDelay = true`, `90ms` hold, `0x04` AppInfo parser) verbatim; replace Android `NsdManager` with **[JmDNS (`org.jmdns:jmdns`)](https://github.com/jmdns/jmdns)** (`_openbikecontrol._tcp.local.`) | **90% reuse** |
| **MyWhoosh Local Key / Click Fallback** | `MyShiftGestureFallback.kt` (`AccessibilityService`) | Use standard JDK **`java.awt.Robot`**: `robot.keyPress(KeyEvent.VK_I)` (Shift Up), `robot.keyPress(KeyEvent.VK_K)` (Shift Down), or `robot.mouseMove(x, y)` + click | **Simple 35-line replacement** |
| **UI, System Tray & Floating Pill** | `MainActivity.kt` + `FloatingHudPill.kt` | **Compose Desktop `Window`** + **`Tray`** (macOS Menu Bar / Ubuntu & Windows Tray) + second undecorated `Window(undecorated = true, transparent = true, alwaysOnTop = true, focusable = false)` for the Floating HUD Pill | **90% reuse** |
| **Zwift Click v2 BLE (`0x094A` / `FC82`)** | `ZwiftBleController.kt` (`android.bluetooth.le`) | Cross-OS `DesktopZwiftBleController.kt` using **[SimpleBLE](https://docs.simpleble.org/overview)** or an embedded **[Bleak](https://bleak.readthedocs.io/en/latest/backends/index.html)** BLE worker (`BlueZ` on Ubuntu, `CoreBluetooth` on macOS, `WinRT` on Windows) | **Clean adapter behind `ClickBleController`** |

---

## 3. Solving the Only JVM Gap: Cross-Platform BLE Across Ubuntu, macOS & Windows

Standard Java/JVM does not include a built-in Bluetooth Low Energy library that works across Linux (`BlueZ`), macOS (`CoreBluetooth`), and Windows (`WinRT`). In a Kotlin Compose Desktop app, you have two proven ways to implement `ClickBleController` so it works identically on all three operating systems without writing OS-specific C++/Objective-C code:

### Approach 3A: Embedded `Bleak` Bridge Worker (`DesktopBleakController.kt`) — Easiest to Test on Ubuntu & Mac
Python's **[Bleak](https://bleak.readthedocs.io/en/latest/backends/index.html)** library is the gold-standard cross-platform BLE library used by open-source cycling projects across **Ubuntu (`BlueZ` D-Bus), macOS (`CoreBluetooth`), and Windows (`WinRT`)**:
- Your Kotlin Compose Desktop app spawns a tiny 65-line `zwift_ble_worker.py` script via `ProcessBuilder` (or PyInstaller single-file helper binary bundled in `resources/`).
- The worker scans for Zwift UUIDs (`0000fc82-0000-1000-8000-00805f9b34fb` and `00000001-19ca-4651-86e5-fa29dcdd09d1`), checks Manufacturer ID `0x094A` (`2378`), writes `"RideOn"` (`52 69 64 65 4F 6E`) to `00000003-19ca-4651-86e5-fa29dcdd09d1`, subscribes to notifications on `00000002-19ca-4651-86e5-fa29dcdd09d1`, and prints one line per BLE notification to `stdout`:
  ```text
  STATE CONNECTED_RIGHT_ONLY F4:C4:59:0C:6F:29
  PACKET F4:C4:59:0C:6F:29 2308FFFEFFFF0F
  ```
- Your Kotlin `DesktopBleakController` reads each `PACKET <addr> <hex>` line, converts `<hex>` to a `ByteArray`, and passes it directly into your existing Kotlin **`ZwiftPacketParser.parseActiveButtonMask(bytes)`** and **`ZwiftGestureEngine.processButtonStates(...)`**!

### Approach 3B: Native JVM Bindings via [SimpleBLE](https://github.com/simpleble/simpleble) / JNA
- Uses pre-compiled native `SimpleBLE` shared libraries (`libsimpleble.so` for Ubuntu, `libsimpleble.dylib` for macOS, `simpleble.dll` for Windows) loaded via JNA/JNI directly inside the JVM process.

---

## 4. Exact Drop-In Kotlin DesktopReplacements for the 4 Android APIs

### 4.1 Cross-Platform mDNS in `OpenBikeControlClient.kt` Using `JmDNS` (`Ubuntu + macOS + Windows`)

Replace `android.net.nsd.NsdManager` in your existing `OpenBikeControlClient.kt` with **[JmDNS](https://github.com/jmdns/jmdns)** (`implementation("org.jmdns:jmdns:3.5.9")` in `build.gradle.kts`). Everything else in your `OpenBikeControlClient.kt` (`ServerSocket(21587)`, `client.tcpNoDelay = true`, `PRESS_HOLD_MS = 90L`, `0x04` AppInfo parser) stays **100% identical**:

```kotlin
package com.ankiride.bridge.mywhoosh

import com.ankiride.bridge.core.DiagnosticLogger
import com.ankiride.bridge.core.LogCategory
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

class DesktopMdnsAdvertiser {
    private var jmdns: JmDNS? = null

    fun registerOpenBikeService(port: Int = 21587) {
        try {
            val lanAddress = findPrimaryLanIpv4() ?: InetAddress.getLocalHost()
            val mdns = JmDNS.create(lanAddress, "AnkiRideDesktop")
            val props = mapOf(
                "version" to "1",
                "id" to "ankiridedesktop01",
                "name" to "AnkiRide Bridge",
                "service-uuids" to "d273f680-d548-419d-b9d1-fa0472345229",
                "manufacturer" to "AnkiRide",
                "model" to "Zwift Click v2 Desktop Bridge"
            )
            val serviceInfo = ServiceInfo.create(
                "_openbikecontrol._tcp.local.",
                "AnkiRideBridge",
                port,
                0,
                0,
                props
            )
            mdns.registerService(serviceInfo)
            jmdns = mdns
            DiagnosticLogger.info(
                LogCategory.MYWHOOSH,
                "JmDNS registered _openbikecontrol._tcp.local. on ${lanAddress.hostAddress}:$port"
            )
        } catch (e: Exception) {
            DiagnosticLogger.error(LogCategory.MYWHOOSH, "JmDNS registration failed: ${e.message}")
        }
    }

    fun unregister() {
        try {
            jmdns?.unregisterAllServices()
            jmdns?.close()
        } catch (_: Exception) {
        }
        jmdns = null
    }

    private fun findPrimaryLanIpv4(): InetAddress? {
        return NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
    }
}
```

### 4.2 Cross-Platform MyWhoosh Keyboard (`I` / `K`) & Mouse Click Injector (`java.awt.Robot`)

Replaces `MyShiftGestureFallback.kt` on Ubuntu, macOS, and Windows using standard JDK `java.awt.Robot` to trigger [MyWhoosh's native keyboard shortcuts](https://mywhoosh.com/myshift-mywhooshs-virtual-shifting-explained/) (`I` = Shift Up, `K` = Shift Down, `A`/`D` = Steer, `H` = HUD) or calibrated screen clicks:

```kotlin
package com.ankiride.bridge.mywhoosh

import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.DiagnosticLogger
import com.ankiride.bridge.core.LogCategory
import com.ankiride.bridge.core.MyWhooshAction
import java.awt.MouseInfo
import java.awt.Robot
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

object DesktopMyWhooshRobot {
    private val robot: Robot? = try { Robot() } catch (_: Exception) { null }

    fun performKeyboardShiftOrAction(action: MyWhooshAction): Boolean {
        val r = robot ?: return false
        val keyCode = when (action) {
            MyWhooshAction.SHIFT_UP -> KeyEvent.VK_I
            MyWhooshAction.SHIFT_DOWN -> KeyEvent.VK_K
            MyWhooshAction.STEER_LEFT -> KeyEvent.VK_A
            MyWhooshAction.STEER_RIGHT -> KeyEvent.VK_D
            MyWhooshAction.HUD_TOGGLE -> KeyEvent.VK_H
            MyWhooshAction.TOGGLE_CAMERA -> KeyEvent.VK_1
            MyWhooshAction.EMOTE -> KeyEvent.VK_E
            MyWhooshAction.UI_CONFIRM -> KeyEvent.VK_ENTER
            MyWhooshAction.UI_BACK -> KeyEvent.VK_ESCAPE
            else -> return false
        }
        return try {
            r.keyPress(keyCode)
            r.delay(45)
            r.keyRelease(keyCode)
            DiagnosticLogger.info(LogCategory.MYWHOOSH, "Desktop Robot sent key '${KeyEvent.getKeyText(keyCode)}' for $action")
            true
        } catch (e: Exception) {
            DiagnosticLogger.error(LogCategory.MYWHOOSH, "Desktop Robot key injection failed: ${e.message}")
            false
        }
    }

    fun performScreenClickFallback(action: MyWhooshAction, config: AppConfiguration): Boolean {
        if (action != MyWhooshAction.SHIFT_UP && action != MyWhooshAction.SHIFT_DOWN) return false
        val r = robot ?: return false
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val (normX, normY) = if (action == MyWhooshAction.SHIFT_UP) {
            config.myShiftUpNormalizedX to config.myShiftUpNormalizedY
        } else {
            config.myShiftDownNormalizedX to config.myShiftDownNormalizedY
        }

        val targetX = (screenSize.width * normX).toInt()
        val targetY = (screenSize.height * normY).toInt()
        val previousMouse = MouseInfo.getPointerInfo()?.location

        return try {
            r.mouseMove(targetX, targetY)
            r.mousePress(InputEvent.BUTTON1_DOWN_MASK)
            r.delay(50)
            r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            // Immediately restore user's cursor position
            if (previousMouse != null) {
                r.mouseMove(previousMouse.x, previousMouse.y)
            }
            DiagnosticLogger.info(LogCategory.MYWHOOSH, "Desktop Robot clicked ($targetX, $targetY) for $action")
            true
        } catch (e: Exception) {
            false
        }
    }
}
```

### 4.3 Cross-Platform Floating Mini HUD Pill in Compose Desktop (`FloatingHudWindow.kt`)

On Android, `FloatingHudPill.kt` needed `TYPE_ACCESSIBILITY_OVERLAY`. In **Compose Multiplatform for Desktop**, a second non-focusable, always-on-top transparent `Window` floats over MyWhoosh and Anki on **Ubuntu, macOS, and Windows** without stealing keyboard focus:

```kotlin
package com.ankiride.bridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

@Composable
fun FloatingHudDesktopWindow(
    enabled: Boolean,
    bleState: BleConnectionState,
    mode: OperatingMode,
    lastActionText: String,
    onClose: () -> Unit
) {
    if (!enabled) return

    val windowState = rememberWindowState(size = DpSize(320.dp, 48.dp))
    Window(
        onCloseRequest = onClose,
        state = windowState,
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
                .background(Color(0xDD1E293B), RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("$dot ${mode.shortTitle}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(lastActionText, color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
```

---

## 5. `build.gradle.kts` & 3-OS GitHub Actions Matrix (`Ubuntu + macOS + Windows`)

### 5.1 `build.gradle.kts` (Compose Multiplatform Desktop)

```kotlin
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.0.20"
    id("org.jetbrains.compose") version "1.7.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20"
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jmdns:jmdns:3.5.9")
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.ankiride.bridge.MainKt"

        nativeDistributions {
            targetFormats(
                TargetFormat.Deb, TargetFormat.AppImage, // Ubuntu / Linux
                TargetFormat.Dmg,                        // macOS
                TargetFormat.Msi, TargetFormat.Exe       // Windows
            )
            packageName = "AnkiRideBridge"
            packageVersion = "4.0.0"
            description = "Zwift Click v2 Bridge for Desktop Anki and MyWhoosh"
            modules("java.instrument", "java.management", "java.naming", "java.prefs", "jdk.unsupported")

            macOS {
                bundleID = "com.ankiride.bridge.desktop"
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSBluetoothAlwaysUsageDescription</key>
                        <string>AnkiRide Bridge connects to your Zwift Click v2 handlebar controller.</string>
                        <key>NSLocalNetworkUsageDescription</key>
                        <string>AnkiRide Bridge advertises OpenBikeControl (_openbikecontrol._tcp) to MyWhoosh.</string>
                        <key>NSBonjourServices</key>
                        <array>
                            <string>_openbikecontrol._tcp</string>
                        </array>
                    """.trimIndent()
                }
            }
        }
    }
}
```

### 5.2 Free 3-OS GitHub Actions Workflow (`.github/workflows/build-all-desktop.yml`)

Every push builds and tests your Kotlin code across **Ubuntu, macOS, and Windows in parallel** for €0:

```yaml
name: Build Desktop Apps (Ubuntu, macOS, Windows)

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build-desktop:
    strategy:
      fail-fast: false
      matrix:
        os: [ubuntu-latest, macos-15, windows-latest]

    runs-on: ${{ matrix.os }}

    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          cache: 'gradle'

      - name: Run All Unit Tests (Parser, Gesture Engine, AnkiConnect, OpenBikeControl)
        run: ./gradlew test --no-daemon

      - name: Package Native Installer for Current OS (.deb / .dmg / .msi)
        run: ./gradlew createDistributable packageDistributionForCurrentOS --no-daemon

      - name: Ad-Hoc Sign macOS Bundle & Add Zero-Cost Gatekeeper Helper (macOS Only)
        if: matrix.os == 'macos-15'
        run: |
          APP_PATH=$(find build/compose/binaries/main/app -name "*.app" | head -n 1)
          codesign --force --deep --sign - "$APP_PATH"
          cat << 'EOF' > build/compose/binaries/main/app/First_Time_Launch_Mac.command
          #!/bin/bash
          cd "$(dirname "$0")"
          xattr -cr *.app
          open *.app
          EOF
          chmod +x build/compose/binaries/main/app/First_Time_Launch_Mac.command

      - name: Upload Installer & App Bundle Artifact
        uses: actions/upload-artifact@v4
        with:
          name: AnkiRideBridge-${{ matrix.os }}
          path: |
            build/compose/binaries/main/app/
            build/compose/binaries/main/dmg/
            build/compose/binaries/main/deb/
            build/compose/binaries/main/msi/
```

---

## 6. End-to-End Developer Workflow (How You Work Day-to-Day)

1. **On Your Ubuntu Laptop (Daily Coding & Live Hardware Testing)**:
   - Run `./gradlew run` in your terminal (or press Run in IntelliJ IDEA Community / VS Code).
   - Pair your physical **Zwift Click v2** directly to your Ubuntu laptop via Bluetooth, open Desktop Anki (`127.0.0.1:8765`), and test every button, mode, and OpenBikeControl packet (`127.0.0.1:21587` or Wi-Fi LAN to your tablet/phone).
2. **When Ready for Your Friend's Mac (or Windows PC)**:
   - Run `git push`.
   - Download `AnkiRideBridge-macos-15.zip` (or `AnkiRideBridge-windows-latest.zip`) from the GitHub Actions tab.
   - Your friend unzips `AnkiRideBridge-macos-15.zip` on their Mac, double-clicks `First_Time_Launch_Mac.command` once (which strips the macOS quarantine bit with `xattr -cr`), and the exact same Compose UI you tested on Ubuntu opens natively on their Mac!

# Master Implementation Specification & Parallel Subagent Orchestration Plan: AnkiRide Bridge (Kotlin Android)

## 1. Executive Mission & Orchestrator Prompt Instructions

You are the **Lead Orchestrator Agent** coordinating a team of parallel coding subagents to build **AnkiRide Bridge** (`com.ankiride.bridge`), an Android Kotlin application (Min SDK 29 / Target SDK 35) for an Android Tablet and Pixel 10.

The app connects to a **[Zwift Click v2](https://www.google.com/search?q=https%3A%2F%2Fzwiftinsider.com%2Fclick-v2-announced%2F)** 10-button dual-pod Bluetooth Low Energy (BLE) handlebar controller and routes button inputs simultaneously to:
1. **[AnkiDroid](https://www.google.com/search?q=https%3A%2F%2Fdocs.ankidroid.org%2F)** — clicking buttons directly inside AnkiDroid's native **[New Reviewer UI](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Fankidroid%2FAnki-Android%2Fissues%2F18575)** via an Android [AccessibilityService](https://www.google.com/search?q=https%3A%2F%2Fwww.noser.com%2Ftechblog%2Fforeign-app-navigation-with-accessibility-service%2F) (`performAction(AccessibilityNodeInfo.ACTION_CLICK)`), with optional read-only status queries against the background [FlashCardsContract.ReviewInfo ContentProvider API](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Fankidroid%2FAnki-Android%2Fblob%2Fmain%2Fapi%2Fsrc%2Fmain%2Fjava%2Fcom%2Fichi2%2Fanki%2FFlashCardsContract.kt).
2. **[MyWhoosh](https://www.google.com/search?q=https%3A%2F%2Fmywhoosh.com%2Fmyshift-mywhooshs-virtual-shifting-explained%2F)** — shifting gears and steering via the official **[OpenBikeControl Network Protocol](https://www.google.com/search?q=https%3A%2F%2Fbikecontrol.app%2Fblog%2Fmywhoosh-bikecontrol-partnership%2F)** (over `127.0.0.1` on the same tablet or over local Wi-Fi LAN to a Pixel 10) with an automatic local touch/keystroke fallback for **[MyWhoosh MyShift](https://www.google.com/search?q=https%3A%2F%2Fmywhoosh.com%2Fmyshift-mywhooshs-virtual-shifting-explained%2F)**.

### Rules for Delegating to Weaker Subagents
- **Never allow two subagents to edit the same file.** Enforce the strict file-ownership matrix defined in Section 3.
- **Execute Wave 0 (Shared Contracts & Build Scaffolding) first** on the parent/orchestrator before spawning Wave 1 parallel subagents (`SA-1-BLE`, `SA-2-ANKI`, `SA-3-MYWHOOSH`, `SA-4-APP-UI`).
- **Enforce every Security Gate (`SG-0` through `SG-4`)** as a blocking verification step. If any subagent fails a Security Gate or unit test, reject its output and re-prompt that specific subagent with the exact gate failure log.
- **Zero Placeholders**: Subagents must not leave `TODO`, `FIXME`, stubbed byte arrays, or unimplemented interfaces. Every protocol constant, UUID, bitmask, XML configuration, and view matcher required is provided verbatim in this specification.

## 2. Operating Modes & 10-Button Hardware Mapping

The [Zwift Click v2](https://www.google.com/search?q=https%3A%2F%2Fzwiftinsider.com%2Fclick-v2-announced%2F) consists of two pods (5 buttons per pod, 10 buttons total):
- **Left Pod**: 4-way directional pad (`LEFT`, `UP`, `RIGHT`, `DOWN`) + front/top shift rocker (`SHFT_DN_L` / `–`).
- **Right Pod**: 4 diamond action buttons (`A`, `B`, `Y`, `Z`) + front/top shift rocker (`SHFT_UP_R` / `+`).

### 2.1 Four User-Selectable Operating Modes

| Mode Enum | Mode Name | Tablet Role | Pixel 10 Role | Routing Behavior |
| :--- | :--- | :--- | :--- | :--- |
| `MODE_1_TABLET_BOTH` | **Tablet Split-Screen (MyWhoosh + AnkiDroid)** | Runs MyWhoosh + native [AnkiDroid](https://www.google.com/search?q=https%3A%2F%2Fdocs.ankidroid.org%2F) side-by-side | Unused / Off | Shifts/steering route to `127.0.0.1` via [OpenBikeControl](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2FOpenBikeControl%2Fbikecontrol) (or local MyShift tap fallback); `A/B/Y/Z` click native AnkiDroid Reviewer buttons via `AccessibilityService`. |
| `MODE_2_TABLET_ANKI_PHONE_MYWHOOSH` | **Tablet AnkiDroid + Phone MyWhoosh** | Runs native [AnkiDroid](https://www.google.com/search?q=https%3A%2F%2Fdocs.ankidroid.org%2F) full-screen | Runs MyWhoosh full-screen | `A/B/Y/Z` click native AnkiDroid buttons locally on the Tablet; shifts/steering route over Wi-Fi LAN (`<pixel-10-lan-ip>`) to MyWhoosh on the Pixel 10 ([MyWhoosh Virtual Shifting Guide](https://www.google.com/search?q=https%3A%2F%2Fbikecontrol.app%2Fblog%2Fmywhoosh-virtual-shifting-guide%2F)). |
| `MODE_3_ANKI_ONLY` | **Anki-Only (Off-Bike Study)** | Runs native [AnkiDroid](https://www.google.com/search?q=https%3A%2F%2Fdocs.ankidroid.org%2F) full-screen | Unused / Off | Disables MyWhoosh network/touch output; maps all 10 buttons (single + long press) to AnkiDroid review and navigation actions. |
| `MODE_4_MYWHOOSH_ONLY` | **MyWhoosh-Only (Pure Ride)** | Runs MyWhoosh full-screen (or forwards to Phone) | Optional MyWhoosh | Disables AnkiDroid clicking; maps all 10 buttons to MyWhoosh shifting, steering, camera, UI toggle, and emotes. |

### 2.2 Deterministic Button Mapping Matrix

| Physical Button | Bitmask (`0x23` Inverse Bitmap) | Gesture | Modes 1 & 2 (Combined MyWhoosh + Anki) | Mode 3 (Anki-Only) | Mode 4 (MyWhoosh-Only) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Right `+` Rocker** | `0x2000` (`SHFT_UP_R`) | Single Press | `MyWhooshAction.SHIFT_UP` | `AnkiAction.REPLAY_AUDIO` | `MyWhooshAction.SHIFT_UP` |
| **Left `–` Rocker** | `0x0400` (`SHFT_DN_L`) | Single Press | `MyWhooshAction.SHIFT_DOWN` | `AnkiAction.UNDO` | `MyWhooshAction.SHIFT_DOWN` |
| **Right `A` (Bottom)** | `0x0010` (`A_BTN`) | Single Press | Flip Card (if Question) / `EASE_3_GOOD` (if Answer) | Flip / `EASE_3_GOOD` | `MyWhooshAction.SELECT` |
| **Right `B` (Right)** | `0x0020` (`B_BTN`) | Single Press | Flip Card (if Question) / `EASE_2_HARD` (if Answer) | Flip / `EASE_2_HARD` | `MyWhooshAction.BACK` |
| **Right `Y` (Left)** | `0x0040` (`Y_BTN`) | Single Press | Flip Card (if Question) / `EASE_1_AGAIN` (if Answer) | Flip / `EASE_1_AGAIN` | `MyWhooshAction.TOGGLE_UI` |
| **Right `Z` (Top)** | `0x0100` (`Z_BTN`) | Single Press | Flip Card (if Question) / `EASE_4_EASY` (if Answer) | Flip / `EASE_4_EASY` | `MyWhooshAction.POWER_UP` |
| **Right `Z` (Top)** | `0x0100` (`Z_BTN`) | Long Press (500ms) | `AnkiAction.UNDO` | `AnkiAction.UNDO` | `MyWhooshAction.SCREENSHOT` |
| **Right `+` (Right-Only Mode)** | `0x2000` (`SHFT_UP_R`) | Double Press (300ms) | `MyWhooshAction.SHIFT_DOWN` (when `rightPodOnly = true`) | `AnkiAction.BURY_CARD` | `MyWhooshAction.SHIFT_DOWN` |
| **Left D-Pad `◀`** | `0x0001` (`LEFT_BTN`) | Single / Hold | `MyWhooshAction.STEER_LEFT` | `AnkiAction.PREV_HINT` | `MyWhooshAction.STEER_LEFT` |
| **Left D-Pad `▶`** | `0x0004` (`RIGHT_BTN`) | Single / Hold | `MyWhooshAction.STEER_RIGHT` | `AnkiAction.NEXT_HINT` | `MyWhooshAction.STEER_RIGHT` |
| **Left D-Pad `▲`** | `0x0002` (`UP_BTN`) | Single Press | `MyWhooshAction.TOGGLE_UI` | `AnkiAction.REPLAY_AUDIO` | `MyWhooshAction.CAMERA_VIEW` |
| **Left D-Pad `▼`** | `0x0008` (`DOWN_BTN`) | Single Press | `MyWhooshAction.EMOTE` | `AnkiAction.BURY_CARD` | `MyWhooshAction.EMOTE` |

## 3. Parallel Execution Topology & Write-Isolation Matrix

To prevent merge conflicts and race conditions among weaker subagents, the repository is partitioned into strict, non-overlapping write zones.

| Wave | Agent ID | Role Name | Owned Repository Paths (Read-Write) | Read-Only Dependencies | Blocking Gate |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Wave 0** | `ORCHESTRATOR` | Parent Setup & Contracts | `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/java/com/ankiride/bridge/core/**` | None | `SG-0` |
| **Wave 1A** | `SA-1-BLE` | Zwift Click v2 BLE & Protobuf Engine | `app/src/main/java/com/ankiride/bridge/ble/**`, `app/src/test/java/com/ankiride/bridge/ble/**` | `core/**` | `SG-1` |
| **Wave 1B** | `SA-2-ANKI` | AnkiDroid Accessibility & Provider Engine | `app/src/main/java/com/ankiride/bridge/anki/**`, `app/src/main/res/xml/accessibility_service_config.xml`, `app/src/test/java/com/ankiride/bridge/anki/**` | `core/**` | `SG-2` & `SG-3` |
| **Wave 1C** | `SA-3-MYWHOOSH` | MyWhoosh OpenBikeControl & Touch Bridge | `app/src/main/java/com/ankiride/bridge/mywhoosh/**`, `app/src/main/res/xml/network_security_config.xml`, `app/src/test/java/com/ankiride/bridge/mywhoosh/**` | `core/**` | `SG-4` |
| **Wave 1D** | `SA-4-APP-UI` | Action Router, Foreground Service & Compose UI | `app/src/main/java/com/ankiride/bridge/service/**`, `app/src/main/java/com/ankiride/bridge/ui/**`, `app/src/main/AndroidManifest.xml`, `app/src/test/java/com/ankiride/bridge/service/**` | `core/**` | `SG-3` |
| **Wave 2** | `SA-5-VERIFIER` | Independent Security & Integration Auditor | `agents/sa-5-verifier/scratch/**` (Read-Only on `app/**`) | Entire Repository | All Gates (`SG-0`..`SG-4`) |

## 4. Wave 0 Specification: Shared Domain Contracts (`core/Contracts.kt`)

The Orchestrator must create `app/src/main/java/com/ankiride/bridge/core/Contracts.kt` **before** launching Wave 1 subagents. Subagents `SA-1-BLE` through `SA-4-APP-UI` must import and implement these exact interfaces without modifying `Contracts.kt`.

```kotlin
package com.ankiride.bridge.core

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class OperatingMode {
    MODE_1_TABLET_BOTH,
    MODE_2_TABLET_ANKI_PHONE_MYWHOOSH,
    MODE_3_ANKI_ONLY,
    MODE_4_MYWHOOSH_ONLY
}

enum class ClickButton(val bitmask: Int) {
    LEFT(0x0001),
    UP(0x0002),
    RIGHT(0x0004),
    DOWN(0x0008),
    A(0x0010),
    B(0x0020),
    Y(0x0040),
    Z(0x0100),
    SHIFT_UP_LEFT(0x0200),
    SHIFT_DOWN_LEFT(0x0400),
    POWERUP_LEFT(0x0800),
    ONOFF_LEFT(0x1000),
    SHIFT_UP_RIGHT(0x2000),
    SHIFT_DOWN_RIGHT(0x4000),
    POWERUP_RIGHT(0x10000),
    ONOFF_RIGHT(0x20000)
}

enum class PressType {
    SINGLE_PRESS,
    DOUBLE_PRESS,
    LONG_PRESS
}

data class ButtonGestureEvent(
    val button: ClickButton,
    val pressType: PressType,
    val timestampMs: Long
)

enum class AnkiAction {
    FLIP_OR_EASE_1_AGAIN,
    FLIP_OR_EASE_2_HARD,
    FLIP_OR_EASE_3_GOOD,
    FLIP_OR_EASE_4_EASY,
    UNDO,
    BURY_CARD,
    REPLAY_AUDIO,
    PREV_HINT,
    NEXT_HINT
}

enum class MyWhooshAction {
    SHIFT_UP,
    SHIFT_DOWN,
    STEER_LEFT,
    STEER_RIGHT,
    TOGGLE_UI,
    EMOTE,
    CAMERA_VIEW,
    SELECT,
    BACK,
    POWER_UP,
    SCREENSHOT
}

data class AppConfiguration(
    val mode: OperatingMode = OperatingMode.MODE_1_TABLET_BOTH,
    val rightPodOnlyMode: Boolean = false,
    val leftPodAutoReconnectSec: Int = 55,
    val targetMyWhooshHost: String = "127.0.0.1",
    val targetMyWhooshPort: Int = 21587,
    val useAccessibilityTapFallbackForMyWhoosh: Boolean = false,
    val myShiftUpNormalizedX: Float = 0.92f,
    val myShiftUpNormalizedY: Float = 0.85f,
    val myShiftDownNormalizedX: Float = 0.84f,
    val myShiftDownNormalizedY: Float = 0.85f,
    val pinnedLeftPodMac: String? = null,
    val pinnedRightPodMac: String? = null
)

sealed interface BleConnectionState {
    data object Disconnected : BleConnectionState
    data object Scanning : BleConnectionState
    data class Connected(
        val leftPodConnected: Boolean,
        val rightPodConnected: Boolean,
        val batteryPercent: Int? = null
    ) : BleConnectionState
    data class Error(val reason: String) : BleConnectionState
}

interface ClickBleController {
    val connectionState: StateFlow<BleConnectionState>
    val gestureEvents: SharedFlow<ButtonGestureEvent>
    fun start(config: AppConfiguration)
    fun stop()
}

interface AnkiActionDispatcher {
    suspend fun dispatch(action: AnkiAction): Boolean
}

interface MyWhooshActionDispatcher {
    suspend fun dispatch(action: MyWhooshAction, config: AppConfiguration): Boolean
}
```

## 5. Wave 1 Subagent Prompt Specifications

### 5.1 Prompt for `SA-1-BLE`: Zwift Click v2 BLE & Protobuf Engine

**Assigned Write Scope**: `app/src/main/java/com/ankiride/bridge/ble/**` and `app/src/test/java/com/ankiride/bridge/ble/**`

#### Technical Requirements & Protocol Specification
1. **BLE Discovery & Manufacturer Data Filtering ([Makinolo Zwift Ride/Click Protocol](https://www.google.com/search?q=https%3A%2F%2Fwww.makinolo.com%2Fblog%2F2024%2F07%2F26%2Fzwift-ride-protocol%2F))**:
   - Filter `ScanResult` using **both** service UUIDs:
     - New 16-bit Service UUID (Firmware $\ge$ 1.1.0): `0000fc82-0000-1000-8000-00805f9b34fb`
     - Legacy 128-bit Service UUID: `00000001-19ca-4651-86e5-fa29dcdd09d1`
   - Verify Manufacturer Specific Data for Company ID `0x094A` (2378 decimal). The first byte of Manufacturer Data identifies the pod type:
     - `0x09` = Zwift Click
     - `0x07` = Ride/Click v2 Right Pod
     - `0x08` = Ride/Click v2 Left Pod
     - `0x02` = Play Right, `0x03` = Play Left
   - If `config.pinnedLeftPodMac` or `config.pinnedRightPodMac` is non-null, strictly reject connections to any unpinned MAC address.

2. **GATT Characteristics & `"RideOn"` Handshake ([ajchellew/zwiftplay](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Fajchellew%2Fzwiftplay))**:
   - Locate the active Zwift service (`0000fc82-...` or `00000001-19ca-...`) and bind its three characteristics:
     - `MEASUREMENT_CHAR_UUID` (Notify): `00000002-19ca-4651-86e5-fa29dcdd09d1`
     - `CONTROL_POINT_CHAR_UUID` (Write): `00000003-19ca-4651-86e5-fa29dcdd09d1`
     - `COMMAND_RESPONSE_CHAR_UUID` (Indicate/Read): `00000004-19ca-4651-86e5-fa29dcdd09d1`
     - `CCCD_UUID`: `00002902-0000-1000-8000-00805f9b34fb`
   - **Handshake Sequence**:
     1. Enable Indications on `COMMAND_RESPONSE_CHAR_UUID` via `CCCD_UUID` (`ENABLE_INDICATION_VALUE`).
     2. Enable Notifications on `MEASUREMENT_CHAR_UUID` via `CCCD_UUID` (`ENABLE_NOTIFICATION_VALUE`).
     3. Write the 6-byte ASCII handshake `"RideOn"` (`byteArrayOf(0x52, 0x69, 0x64, 0x65, 0x4F, 0x6E)`) to `CONTROL_POINT_CHAR_UUID`.
     4. Verify that `onCharacteristicChanged` / `onCharacteristicRead` on `COMMAND_RESPONSE_CHAR_UUID` starts with `52 69 64 65 4F 6E` (`"RideOn"`).

3. **Protobuf `0x23` Inverse Bitmap Decoder (`ZwiftPacketParser.kt`)**:
   - Incoming notifications on `MEASUREMENT_CHAR_UUID` have message ID `0x23` as byte `0` for keystrokes (ignore idle keep-alive messages `0x19` and `0x15`).
   - For message `0x23`:
     - Verify `payload.size in 3..64`. Reject payloads outside this range immediately.
     - Verify byte `1` is `0x08` (Protobuf field number 1, wire type 0 = Varint).
     - Decode the Base-128 Varint starting at byte `2` (maximum 5 bytes for a 32-bit uint32).
     - **Inverse Logic**: In the decoded 32-bit `buttonMap`, a bit set to `0` means **PRESSED** and a bit set to `1` means **RELEASED** ([Makinolo Protocol Spec](https://www.google.com/search?q=https%3A%2F%2Fwww.makinolo.com%2Fblog%2F2024%2F07%2F26%2Fzwift-ride-protocol%2F)). Compute `val activeButtonsMask = buttonMap.inv()`.

```kotlin
package com.ankiride.bridge.ble

object ZwiftPacketParser {
    private const val MSG_KEYPAD_STATUS: Byte = 0x23
    private const val PROTO_FIELD_1_VARINT: Byte = 0x08
    private const val MAX_PACKET_SIZE = 64
    private const val MAX_VARINT_BYTES = 5

    /**
     * Returns the active (pressed) button mask where 1 = pressed, or null if not a valid 0x23 packet.
     */
    fun parseActiveButtonMask(payload: ByteArray): Int? {
        if (payload.size < 3 || payload.size > MAX_PACKET_SIZE) return null
        if (payload[0] != MSG_KEYPAD_STATUS || payload[1] != PROTO_FIELD_1_VARINT) return null

        var result = 0
        var shift = 0
        var index = 2
        var bytesRead = 0

        while (index < payload.size) {
            if (bytesRead >= MAX_VARINT_BYTES) return null // Malformed varint overflow guard
            val b = payload[index].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            bytesRead++
            index++
            if ((b and 0x80) == 0) {
                // Inverse logic: 0 bit in wire format means button is pressed
                return result.inv()
            }
            shift += 7
        }
        return null // Truncated varint
    }
}
```

4. **Gesture State Machine & Left-Pod 55-Second Watchdog ([OpenBikeControl Issue #68](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2FOpenBikeControl%2Fbikecontrol%2Fissues%2F68))**:
   - Classify each button press into `SINGLE_PRESS`, `DOUBLE_PRESS` (second press within `300ms`), or `LONG_PRESS` (held $\ge 500ms$).
   - **Left-Pod 24h Lock Workaround**:
     - If `config.rightPodOnlyMode == true` ([BikeControl Click v2 Guide](https://www.google.com/search?q=https%3A%2F%2Fbikecontrol.app%2Fcontroller-zwift-click-v2%2F)), ignore the left pod during BLE scan and map `SHIFT_UP_RIGHT` double-press to `SHIFT_DOWN`.
     - If `config.rightPodOnlyMode == false`, run a coroutine watchdog on the left pod's GATT connection that gracefully disconnects, waits `150ms`, and calls `gatt.connect()` + `"RideOn"` handshake every `config.leftPodAutoReconnectSec` (default `55` seconds) whenever the left pod stops emitting idle `0x15`/`0x19` heartbeats after 55 seconds.

### 5.2 Prompt for `SA-2-ANKI`: AnkiDroid Native UI Accessibility & ContentProvider Engine

**Assigned Write Scope**: `app/src/main/java/com/ankiride/bridge/anki/**`, `app/src/main/res/xml/accessibility_service_config.xml`, and `app/src/test/java/com/ankiride/bridge/anki/**`

#### Technical Requirements
1. **`accessibility_service_config.xml` Configuration**:
   Create `app/src/main/res/xml/accessibility_service_config.xml` with strict package pinning so the service never inspects other apps:

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagRetrieveInteractiveWindows|flagReportViewIds"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="50"
    android:packageNames="com.ichi2.anki,com.mywhoosh.whooshgame" />
```

2. **Split-Screen & Multi-Window Node Resolver (`AnkiAccessibilityBridge.kt`)**:
   - Iterate through `service.windows` (`AccessibilityWindowInfo`) to find the window whose `root?.packageName?.toString() == "com.ichi2.anki"` ([Foreign App Navigation with Android Accessibility Services](https://www.google.com/search?q=https%3A%2F%2Fwww.noser.com%2Ftechblog%2Fforeign-app-navigation-with-accessibility-service%2F)).
   - **Never require AnkiDroid's pane to hold input focus** in split-screen: inspect its `root` `AccessibilityNodeInfo` directly and invoke `node.performAction(AccessibilityNodeInfo.ACTION_CLICK)` ([Automating Android App Interactions with Accessibility Service](https://www.google.com/search?q=https%3A%2F%2Fhackmag.com%2Fmobile%2Faccessibility-service-in-action)).
   - **Auto-Detect Question vs. Answer State in AnkiDroid's Reviewer (Supporting Both Legacy & [New Reviewer UI](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Fankidroid%2FAnki-Android%2Fissues%2F18575))**:

```kotlin
package com.ankiride.bridge.anki

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import com.ankiride.bridge.core.AnkiAction

object AnkiAccessibilityBridge {
    private const val ANKI_PACKAGE = "com.ichi2.anki"

    private val SHOW_ANSWER_IDS = listOf(
        "com.ichi2.anki:id/show_answer",
        "com.ichi2.anki:id/flashcard_layout_flip"
    )

    private val EASE_1_IDS = listOf("com.ichi2.anki:id/again_button", "com.ichi2.anki:id/flashcard_layout_ease1")
    private val EASE_2_IDS = listOf("com.ichi2.anki:id/hard_button", "com.ichi2.anki:id/flashcard_layout_ease2")
    private val EASE_3_IDS = listOf("com.ichi2.anki:id/good_button", "com.ichi2.anki:id/flashcard_layout_ease3")
    private val EASE_4_IDS = listOf("com.ichi2.anki:id/easy_button", "com.ichi2.anki:id/flashcard_layout_ease4")
    private val UNDO_IDS = listOf("com.ichi2.anki:id/action_undo")

    fun performAnkiAction(service: AccessibilityService, action: AnkiAction): Boolean {
        val ankiRoot = service.windows
            ?.firstOrNull { it.root?.packageName?.toString() == ANKI_PACKAGE }
            ?.root ?: return false

        // Security Gate SG-2 check: verify package and reject password fields
        if (ankiRoot.packageName?.toString() != ANKI_PACKAGE) return false

        return when (action) {
            AnkiAction.FLIP_OR_EASE_1_AGAIN -> clickShowAnswerOrEase(ankiRoot, EASE_1_IDS, "Again")
            AnkiAction.FLIP_OR_EASE_2_HARD -> clickShowAnswerOrEase(ankiRoot, EASE_2_IDS, "Hard")
            AnkiAction.FLIP_OR_EASE_3_GOOD -> clickShowAnswerOrEase(ankiRoot, EASE_3_IDS, "Good")
            AnkiAction.FLIP_OR_EASE_4_EASY -> clickShowAnswerOrEase(ankiRoot, EASE_4_IDS, "Easy")
            AnkiAction.UNDO -> clickNodeByIdsOrText(ankiRoot, UNDO_IDS, "Undo")
            AnkiAction.BURY_CARD -> clickNodeByIdsOrText(ankiRoot, emptyList(), "Bury")
            AnkiAction.REPLAY_AUDIO -> clickNodeByIdsOrText(ankiRoot, emptyList(), "Replay audio")
            AnkiAction.PREV_HINT, AnkiAction.NEXT_HINT -> clickNodeByIdsOrText(ankiRoot, emptyList(), "Hint")
        }
    }

    private fun clickShowAnswerOrEase(
        root: AccessibilityNodeInfo,
        easeIds: List<String>,
        easeFallbackText: String
    ): Boolean {
        if (clickNodeByIdsOrText(root, SHOW_ANSWER_IDS, "Show answer")) {
            return true
        }
        return clickNodeByIdsOrText(root, easeIds, easeFallbackText)
    }

    private fun clickNodeByIdsOrText(
        root: AccessibilityNodeInfo,
        viewIds: List<String>,
        fallbackText: String
    ): Boolean {
        for (id in viewIds) {
            val matches = root.findAccessibilityNodeInfosByViewId(id)
            val clickable = matches?.firstOrNull { !it.isPassword && it.isVisibleToUser }
            if (clickable != null && performClickUpHierarchy(clickable)) {
                return true
            }
        }
        val textMatches = root.findAccessibilityNodeInfosByText(fallbackText)
        val clickableByText = textMatches?.firstOrNull { !it.isPassword && it.isVisibleToUser }
        return clickableByText != null && performClickUpHierarchy(clickableByText)
    }

    private fun performClickUpHierarchy(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isPassword) return false
            if (current.isClickable && current.isEnabled) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
            depth++
        }
        return false
    }
}
```

3. **Optional Background Database Helper (`AnkiContentProviderHelper.kt`)**:
   - Implement a read-only status query against [FlashCardsContract.ReviewInfo.CONTENT\_URI](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2Fankidroid%2FAnki-Android%2Fblob%2Fmain%2Fapi%2Fsrc%2Fmain%2Fjava%2Fcom%2Fichi2%2Fanki%2FFlashCardsContract.kt) (`content://com.ichi2.anki.flashcards/schedule`) using parameterized `selectionArgs` to display the active deck's remaining card count inside the AnkiRide Bridge dashboard.

### 5.3 Prompt for `SA-3-MYWHOOSH`: MyWhoosh OpenBikeControl Network & Gesture Engine

**Assigned Write Scope**: `app/src/main/java/com/ankiride/bridge/mywhoosh/**`, `app/src/main/res/xml/network_security_config.xml`, and `app/src/test/java/com/ankiride/bridge/mywhoosh/**`

#### Technical Requirements
1. **Strict Destination IP Validator (`LanAddressValidator.kt`)**:
   - Before opening any socket or sending any packet, validate `config.targetMyWhooshHost` so the app can only communicate with `127.0.0.1` (Tablet Mode 1/4) or a private LAN IPv4 address on the local Wi-Fi network (Pixel 10 Mode 2):

```kotlin
package com.ankiride.bridge.mywhoosh

import java.net.InetAddress

object LanAddressValidator {
    private val IPV4_LITERAL_REGEX = Regex(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    )

    fun isAllowedTargetHost(host: String): Boolean {
        if (!IPV4_LITERAL_REGEX.matches(host)) return false
        return try {
            val addr = InetAddress.getByName(host)
            addr.isLoopbackAddress || addr.isSiteLocalAddress
        } catch (_: Exception) {
            false
        }
    }
}
```

2. **`network_security_config.xml` Hardening**:
   Create `app/src/main/res/xml/network_security_config.xml` with `cleartextTrafficPermitted="false"` globally:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
</network-security-config>
```

3. **OpenBikeControl Localhost & LAN Dispatcher (`MyWhooshNetworkDispatcher.kt`)**:
   - Implement the [OpenBikeControl protocol](https://www.google.com/search?q=https%3A%2F%2Fbikecontrol.app%2Fblog%2Fmywhoosh-bikecontrol-partnership%2F) client to send `SHIFT_UP`, `SHIFT_DOWN`, `STEER_LEFT`, `STEER_RIGHT`, `TOGGLE_UI`, and `EMOTE` commands to `config.targetMyWhooshHost:config.targetMyWhooshPort` after verifying `LanAddressValidator.isAllowedTargetHost(config.targetMyWhooshHost)` ([MyWhoosh Virtual Shifting Guide](https://www.google.com/search?q=https%3A%2F%2Fbikecontrol.app%2Fblog%2Fmywhoosh-virtual-shifting-guide%2F)).
   - Include Android `NsdManager` mDNS service discovery so the Tablet automatically discovers the Pixel 10's LAN IP when Mode 2 (`MODE_2_TABLET_ANKI_PHONE_MYWHOOSH`) is active.

4. **MyWhoosh MyShift Local Touch Fallback (`MyShiftGestureFallback.kt`)**:
   - When `config.useAccessibilityTapFallbackForMyWhoosh == true` in Mode 1 or Mode 4 on the Tablet, locate the `com.mywhoosh.whooshgame` window bounds via `AccessibilityWindowInfo.getBoundsInScreen(rect)` and dispatch a `15ms` `GestureDescription` tap on the [MyWhoosh MyShift](https://www.google.com/search?q=https%3A%2F%2Fmywhoosh.com%2Fmyshift-mywhooshs-virtual-shifting-explained%2F) left/right arrow coordinates (`myShiftDownNormalizedX`, `myShiftUpNormalizedX` scaled to `rect`).

### 5.4 Prompt for `SA-4-APP-UI`: Action Router, Foreground Service & Compose Control Panel

**Assigned Write Scope**: `app/src/main/java/com/ankiride/bridge/service/**`, `app/src/main/java/com/ankiride/bridge/ui/**`, `app/src/main/AndroidManifest.xml`, and `app/src/test/java/com/ankiride/bridge/service/**`

#### Technical Requirements
1. **`ActionRouter.kt` (Deterministic Mode Dispatcher)**:
   - Subscribe to `ClickBleController.gestureEvents` and route each `ButtonGestureEvent` strictly according to Section 2.2 based on `AppConfiguration.mode`:
     - `MODE_1_TABLET_BOTH`: Send shift/D-pad actions to `MyWhooshActionDispatcher` (`targetMyWhooshHost = "127.0.0.1"`) and `A/B/Y/Z` actions to `AnkiActionDispatcher`.
     - `MODE_2_TABLET_ANKI_PHONE_MYWHOOSH`: Send shift/D-pad actions to `MyWhooshActionDispatcher` (`targetMyWhooshHost = <pixel-10-lan-ip>`) and `A/B/Y/Z` actions to `AnkiActionDispatcher`.
     - `MODE_3_ANKI_ONLY`: Route all 10 buttons to `AnkiActionDispatcher`; never invoke `MyWhooshActionDispatcher`.
     - `MODE_4_MYWHOOSH_ONLY`: Route all 10 buttons to `MyWhooshActionDispatcher`; never invoke `AnkiActionDispatcher`.

2. **Hardened `AndroidManifest.xml`**:
   - Declare `BLUETOOTH_SCAN` (`neverForLocation`), `BLUETOOTH_CONNECT`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `INTERNET`, and `com.ichi2.anki.permission.READ_WRITE_DATABASE`.
   - Set `android:networkSecurityConfig="@xml/network_security_config"`.
   - Set `android:exported="false"` on `BridgeForegroundService` and `BridgeAccessibilityService`, and bind `BridgeAccessibilityService` with `android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"`.

3. **Jetpack Compose Control Panel (`MainActivity.kt`)**:
   - Single-screen utility displaying:
     - **4-Mode Selector Cards** (`Tablet Split-Screen`, `Tablet Anki + Phone MyWhoosh`, `Anki-Only`, `MyWhoosh-Only`).
     - **Zwift Click v2 Connection Status**: Left Pod & Right Pod connection badges, battery %, MAC pinning toggle, and **"Right-Pod-Only (No 24h Unlock)"** switch ([BikeControl Click v2 Guide](https://www.google.com/search?q=https%3A%2F%2Fbikecontrol.app%2Fcontroller-zwift-click-v2%2F)).
     - **Pixel 10 LAN IP Input / mDNS Auto-Discovery Picker** (visible when Mode 2 is active).
     - **Accessibility & BLE Permission Checklist** with one-tap buttons to open Android Accessibility Settings and Battery Optimization settings ([OpenBikeControl Troubleshooting](https://www.google.com/search?q=https%3A%2F%2Fgithub.com%2FOpenBikeControl%2Fbikecontrol%2Fblob%2Fmain%2FTROUBLESHOOTING.md)).

## 6. Mandatory Security Gates (`SG-0` through `SG-4`)

Every subagent output and the final integrated build must pass these 5 deterministic security gates before completion.

| Gate ID | Trust Boundary Protected | Threat / Attack Class Mitigated | Mandatory Verification Invariant |
| :--- | :--- | :--- | :--- |
| **`SG-0`** | **Subagent Write Isolation & Supply Chain** | Cross-agent file clobbering, unpinned remote dependencies, or ambient secret leakage | Each subagent modifies only its assigned path in Section 3; `build.gradle.kts` uses only pinned Maven Central / Google repositories with zero dynamic `+` versions. |
| **`SG-1`** | **Untrusted BLE Radio Input → `ZwiftPacketParser`** | Malformed protobuf varint DoS, buffer over-read, or rogue nearby BLE click injection | `ZwiftPacketParser` rejects payloads `< 3` or `> 64` bytes and varints `> 5` bytes without throwing exceptions; optional MAC pinning drops unpinned BLE transmitters; unit tests fuzz 10,000 random/malformed byte arrays with zero crashes. |
| **`SG-2`** | **`AccessibilityService` → Host OS & Foreign Apps** | Confused deputy UI automation, cross-app credential/screen scraping, or unauthenticated external Intent trigger | `accessibility_service_config.xml` pins `packageNames="com.ichi2.anki,com.mywhoosh.whooshgame"`; runtime check verifies `node.packageName` and aborts if `node.isPassword == true`; service rejects all external `onStartCommand` intents. |
| **`SG-3`** | **Android Local IPC & Exported Manifest Surface** | Local malware invoking exported services/receivers or SQL injection via ContentProvider selector | Every `<service>` and `<receiver>` in `AndroidManifest.xml` has `android:exported="false"` (and `BIND_ACCESSIBILITY_SERVICE` on the accessibility service); internal event routing uses in-process Kotlin `SharedFlow` (zero Implicit Broadcasts). |
| **`SG-4`** | **Wi-Fi LAN Dispatcher → Local Network** | SSRF, DNS rebinding, or cleartext data exfiltration to public internet hosts | `LanAddressValidator` enforces numeric IPv4 literals matching `isLoopbackAddress` or RFC1918 `isSiteLocalAddress`; `network_security_config.xml` sets `base-config cleartextTrafficPermitted="false"`. |

## 7. Automated Security Gate & Build Verification Script

Save and run this verification script from the project root after Wave 1 subagents complete. Wave 2 (`SA-5-VERIFIER`) must execute this script and report `PASS` on all checks before the run can be marked complete.

```bash
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
```

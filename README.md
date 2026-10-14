# AnkiRide Bridge (`com.ankiride.bridge`)

An Android application (Kotlin, Jetpack Compose, Min SDK 29 / Target SDK 35) that connects a **Zwift Click v2** (10-button dual-pod BLE handlebar controller) and dynamically routes button gestures simultaneously to:

1. **AnkiDroid** — Automated card review clicking via an Android `AccessibilityService` (`ACTION_CLICK`) without requiring input focus, with auto-detection of Question vs. Answer state.
2. **MyWhoosh** — Virtual gear shifting and steering via the official **OpenBikeControl Network Protocol** (TCP port 21587 over localhost `127.0.0.1` or Wi-Fi LAN with mDNS discovery), featuring an automatic local accessibility touch fallback for **MyWhoosh MyShift**.

---

## Operating Modes

| Mode | Role / Routing |
| :--- | :--- |
| **Mode 1: Tablet Split-Screen** | Runs MyWhoosh + AnkiDroid side-by-side. Shifts and steering route to `127.0.0.1` via OpenBikeControl (or MyShift tap fallback); `A/B/Y/Z` clicks native AnkiDroid review buttons. |
| **Mode 2: Tablet AnkiDroid + Phone MyWhoosh** | Runs AnkiDroid full-screen on Tablet; MyWhoosh full-screen on a second device (e.g., Pixel 10). Shifts and steering route over Wi-Fi LAN to MyWhoosh via OpenBikeControl with mDNS auto-discovery. |
| **Mode 3: Anki-Only (Study)** | Disables MyWhoosh output; maps all 10 buttons (single + double + long press) to AnkiDroid review, hint navigation, and audio replay. |
| **Mode 4: MyWhoosh-Only (Ride)** | Disables AnkiDroid clicking; maps all 10 buttons to MyWhoosh shifting, steering, UI toggle, camera views, powerups, and emotes. |

---

## 10-Button Controller Mapping Matrix

| Button | Inverse Bitmask | Modes 1 & 2 (Combined) | Mode 3 (Anki-Only) | Mode 4 (MyWhoosh-Only) |
| :--- | :--- | :--- | :--- | :--- |
| **Right `+` Rocker** | `0x2000` | `SHIFT_UP` | `REPLAY_AUDIO` | `SHIFT_UP` |
| **Right `+` Double-Press** | `0x2000` (Right-Pod-Only) | `SHIFT_DOWN` | `BURY_CARD` | `SHIFT_DOWN` |
| **Left `–` Rocker** | `0x0400` | `SHIFT_DOWN` | `UNDO` | `SHIFT_DOWN` |
| **Right `A` (Bottom)** | `0x0010` | Flip / `EASE_3_GOOD` | Flip / `EASE_3_GOOD` | `SELECT` |
| **Right `B` (Right)** | `0x0020` | Flip / `EASE_2_HARD` | Flip / `EASE_2_HARD` | `BACK` |
| **Right `Y` (Left)** | `0x0040` | Flip / `EASE_1_AGAIN` | Flip / `EASE_1_AGAIN` | `TOGGLE_UI` |
| **Right `Z` (Top)** | `0x0100` | Flip / `EASE_4_EASY` | Flip / `EASE_4_EASY` | `POWER_UP` |
| **Right `Z` Long Press** | `0x0100` (500ms) | `UNDO` | `UNDO` | `SCREENSHOT` |
| **Left D-Pad `◀`** | `0x0001` | `STEER_LEFT` | `PREV_HINT` | `STEER_LEFT` |
| **Left D-Pad `▶`** | `0x0004` | `STEER_RIGHT` | `NEXT_HINT` | `STEER_RIGHT` |
| **Left D-Pad `▲`** | `0x0002` | `TOGGLE_UI` | `REPLAY_AUDIO` | `CAMERA_VIEW` |
| **Left D-Pad `▼`** | `0x0008` | `EMOTE` | `BURY_CARD` | `EMOTE` |

---

## Security Invariants (`SG-0` through `SG-4`)

- **SG-0**: Strict write isolation and pinned dependency versions (no dynamic `+`).
- **SG-1**: Protobuf `0x23` parser validates bounds (`MAX_PACKET_SIZE = 64`, `MAX_VARINT_BYTES = 5`) and handles 10,000 malformed fuzzed inputs with zero exceptions.
- **SG-2**: Accessibility service strictly pinned to `com.ichi2.anki` and `com.mywhoosh.whooshgame`, aborts if `isPassword == true`, and rejects external `onStartCommand` intents.
- **SG-3**: All services and receivers declared with `android:exported="false"`; internal events use in-process Kotlin flows.
- **SG-4**: Destination IP validator strictly allows only `127.0.0.1` or RFC1918 site-local addresses (`isLoopbackAddress || isSiteLocalAddress`); global network config disables cleartext HTTP traffic.

---

## Build & Verification

```bash
# Run security gates and unit test suite
./verify.sh

# Build debug APK
./gradlew assembleDebug
```

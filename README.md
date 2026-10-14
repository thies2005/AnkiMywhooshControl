# AnkiRide Bridge (`com.ankiride.bridge`)

A 100% standalone Android application (Kotlin, Jetpack Compose, Min SDK 29 / Target SDK 35) that connects a **Zwift Click v2** (10-button dual-pod BLE handlebar controller) and dynamically routes inputs to:

1. **AnkiDroid** — Automated card review clicking via an Android `AccessibilityService` (`ACTION_CLICK`) without requiring input focus, using a 3-layer clicking engine (View IDs -> Bottom-bar labels -> Geometric column tap) with a 350ms post-flip bounce guard.
2. **MyWhoosh** — Native virtual shifting and steering via a built-in **`_openbikecontrol._tcp` binary server & mDNS advertiser** (TCP port 21587 over `127.0.0.1` or Wi-Fi LAN), completely replacing third-party apps like BikeControl or Zwift, plus an automatic accessibility touch fallback for **MyWhoosh MyShift**.

---

## Zero-Quirk Architecture Highlights

- **Default 1-Pod Mode (Right Pod Only)**: Uses the Right Pod (`A`, `B`, `Y`, `Z` + top `+` rocker) which has no 24-hour sleep lock and never requires the Zwift app. Instant mechanical shifting (**Top `+` Rocker = Shift Up**, **Top `Z` Button = Shift Down**) and 100% card review coverage (**`Y` = Again**, **`B` = Hard**, **`A` = Good**, **Long-Press `A` = Easy**, **Long-Press `Y` = Undo**).
- **Optional 2-Pod Full Mode**: Enable the Left Pod (`–` Rocker + D-Pad) with automatic 55-second watchdog auto-reconnect.
- **Built-In OpenBikeControl Server**: Advertises `_openbikecontrol._tcp` on port 21587. When MyWhoosh opens on the tablet or a phone over Wi-Fi, it auto-discovers this app and connects directly.
- **3-Layer AnkiDroid Clicking + 350ms Bounce Guard**: Automatically flips cards, grades them, and prevents accidental double-tap grading on bumpy sprints.

---

## Operating Modes

| Mode | Role / Routing |
| :--- | :--- |
| **Mode 1: Tablet Split-Screen** | Runs MyWhoosh + AnkiDroid side-by-side. MyWhoosh connects to `127.0.0.1:21587`; AnkiDroid buttons clicked via AccessibilityService. |
| **Mode 2: Tablet AnkiDroid + Phone MyWhoosh** | Runs AnkiDroid full-screen on Tablet; MyWhoosh on phone. Phone discovers the Tablet over Wi-Fi LAN via mDNS (`_openbikecontrol._tcp`). |
| **Mode 3: Anki-Only (Study)** | Disables MyWhoosh output; maps all buttons to AnkiDroid review, hints, and navigation. |
| **Mode 4: MyWhoosh-Only (Ride)** | Disables AnkiDroid clicking; maps buttons to MyWhoosh shifting, steering, camera, powerup, and emotes. |

---

## Button Mapping Proposal

| Button | Gesture | 1-Pod Default (`rightPodOnlyNoZwift = true`) | 2-Pod Full Mode (`rightPodOnlyNoZwift = false`) | Mode 3 (Anki-Only) | Mode 4 (MyWhoosh-Only) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Right `+` Rocker** | Single Click | **Shift Up (`0x01`)** | **Shift Up (`0x01`)** | Flip / Good (`3`) | Shift Up (`0x01`) |
| **Right `+` Rocker** | Long Press (450ms) | **Shift Down (`0x02`)** | Shift Up (+2) | Undo | Shift Down (`0x02`) |
| **Right `Z` (Top)** | Single Click | **Shift Down (`0x02`)** | **Flip / Easy (`4`)** | Flip / Easy (`4`) | Shift Down (`0x02`) |
| **Right `Z` (Top)** | Long Press (450ms) | **Undo** | **Undo** | Undo | Screenshot (`0x24`) |
| **Right `A` (Bottom)** | Single Click | **Flip / Good (`3`)** | **Flip / Good (`3`)** | Flip / Good (`3`) | UI Confirm (`0x14`) |
| **Right `A` (Bottom)** | Long Press (450ms) | **Flip / Easy (`4`)** | **Flip / Easy (`4`)** | Flip / Easy (`4`) | Power-up (`0x50`) |
| **Right `B` (Right)** | Single Click | **Flip / Hard (`2`)** | **Flip / Hard (`2`)** | Flip / Hard (`2`) | UI Back (`0x15`) |
| **Right `Y` (Left)** | Single Click | **Flip / Again (`1`)** | **Flip / Again (`1`)** | Flip / Again (`1`) | HUD Toggle (`0x44`) |
| **Right `Y` (Left)** | Long Press (450ms) | **Undo** | **Undo** | Undo | Emote (`0x20`) |
| **Left `–` Rocker** | Single Click | *(Ignored)* | **Shift Down (`0x02`)** | Undo | Shift Down (`0x02`) |
| **Left D-Pad `◀` / `▶`** | Single / Hold | *(Ignored)* | **Steer Left / Right (`0x18`/`0x19`)** | Flip Card | Steer Left / Right |
| **Left D-Pad `▲` / `▼`** | Single Click | *(Ignored)* | **Camera (`0x40`) / Emote (`0x20`)** | Undo | Camera / Emote |

---

## Build & Verification

```bash
# Run security gates and unit test suite
./verify.sh

# Build debug APK
./gradlew assembleDebug
```

# ClickControl design language

ClickControl helps a rider see what is connected, choose a familiar controller setup, and review cards without thinking about network plumbing. Android is a touch application; Linux desktop is a keyboard-and-pointer application. They share language, colors, preset behavior and hierarchy, while adapting navigation and density to their platform.

## Information architecture

- **Control**: connection status, active preset, Anki destination, start/stop, and readable mappings. Show the selected physical pod and its ownership explicitly. Connectivity is a state, not a warning unless an action is needed.
- **Presets**: a named library with create, duplicate, rename, save, reset and delete. Single press and hold are separate bindings. Unsaved edits must be visible; switching presets must not silently lose edits. Preserve at least one usable preset. The editor and actual button routing must share the same resolver.
- **Settings**: Bluetooth ownership and pins, Anki connection, optional feedback, app startup and diagnostics. Advanced technical details stay here rather than on every control.

Old mixed mappings are imported as named editable presets. “Legacy” is an internal compatibility term and never a user-facing mode. Editing a mapping must not silently change controller ownership or Anki destination. Presets describe actions; connection settings describe which controllers ClickControl owns.

## Semantic colors

| Role | Color | Use |
| --- | --- | --- |
| Background | #101B22 | Main app canvas |
| Surface | #182A33 | Cards and navigation |
| Elevated surface | #213943 | Editor controls and selected groups |
| Primary | #70D8C8 | Main actions, selection and focus |
| On primary | #062B28 | Text and icons on primary buttons |
| Main text | #ECF5F4 | Titles, values and button labels |
| Secondary text | #B5C9C8 | Supporting explanations |
| Outline | #829B9A | Input and card boundaries |

Map these roles explicitly through Material 3, including button foregrounds, text fields, menus, tabs, selected states and floating HUDs. Hardware button colors can help recognition, but a label must always identify the button. Never depend only on color to communicate an action or connection state.

## Design goals and verification status

The palette above is implemented in both platform themes. The following typography, spacing, adaptive layout and accessibility statements are design goals; they are not a claim that every screen or accessibility path has been verified. See [AUDIT.md](AUDIT.md) for observed coverage.

## Type and spacing

Use the platform sans-serif family with a consistent hierarchy: a modest app title, clear section headings, readable body text, and secondary supporting text. Do not put an action sentence inside a small hardware circle. Use the physical label (Y, Z, A, B, +) as a badge next to the full action label. Keep technical addresses and diagnostics secondary.

Use an 8 dp spacing rhythm with 16–24 dp section padding. Use 12–16 dp card corners and restrained outlines. Avoid heavily nested cards and arbitrary accent colors. Buttons need enough horizontal space for a whole label: prefer “Connect”, “Save preset”, “Duplicate”, and “Test Anki”. Let supporting descriptions wrap naturally. Never shrink text to make a layout fit.

## Platform behavior

Android uses bottom navigation, one primary column, scrollable content and touch targets of at least 48 dp. Respect system bars and keyboard insets. Put lengthy explanations below controls, not inside buttons. At larger text sizes, stack actions rather than squeezing labels into vertical letter columns.

Desktop uses persistent navigation and a split layout where space permits; the same content becomes a single scrollable column in compact windows. All actions need visible text, keyboard focus and predictable order. Preserve useful window size; no essential tab or action may fall outside a clipped horizontal strip.

## Verification

Inspect actual screenshots at phone width, desktop wide and desktop compact. Exercise preset creation, edits, save, switching, rename, duplicate, delete and restart persistence. Confirm a custom mapping routes the selected physical button to the shown action. Check text on every filled/outlined button and selected menu item. Screenshots establish visible layout, not full accessibility compliance or real BLE behavior.

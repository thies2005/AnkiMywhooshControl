# ClickControl verification status

Local verification on 3 November 2026 passed `./verify.sh`; the test XML reports record 62 Android debug unit tests and 98 desktop unit tests with zero failures or errors. These are unit-test results, not evidence of real Bluetooth or target-application operation. The Android release test report is a separate run and should not be added to the debug count. The Python worker policy test and packaged Linux `--self-test` passed locally; the self-test exercises parser, gestures, settings, routing and local server startup.

An Android emulator walkthrough covered a blank preset, a binding edit, unsaved-draft confirmation and recreation, save and restart persistence. Linux wide and compact overview screenshots were inspected, but desktop editor interactions were not fully exercised by native pointer automation. These observations do not establish accessibility compliance.

Still requiring physical or target-environment testing: controller side advertisements, sleep and reconnection behavior, real AnkiConnect and MyWhoosh interoperability, Linux tray/autostart behavior across sessions, macOS and Windows operation, and complete keyboard/accessibility coverage. MyWhoosh TCP app-info parsing assumes a complete message per read, so fragmentation and protocol negotiation need an integration test. AnkiConnect's local answer-visibility state may become out of sync if controller review is mixed with mouse or keyboard review.

See [Setup](docs/SETUP.md), [Development](docs/DEVELOPMENT.md), and [Troubleshooting](docs/TROUBLESHOOTING.md).

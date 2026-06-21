# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

FocusMode is a single-module Android app (Kotlin + Jetpack Compose / Material3) that blocks
notifications and phone calls from anyone not on an "allowed contacts" list while a Focus Mode
toggle is on. There is no remote backend — all state is local to the device.

- `applicationId` / namespace: `com.example.focusmode`, minSdk 29, target/compileSdk 35.
- Single `:app` module, no navigation library — one Activity with manually-managed tab state.

## Common commands

Run from the repo root. On Windows use `gradlew.bat`; from a POSIX shell (e.g. git-bash) use `./gradlew`.

```
gradlew.bat assembleDebug              # build debug APK
gradlew.bat installDebug                # build + install on a connected device/emulator
gradlew.bat test                        # run JVM unit tests (app/src/test)
gradlew.bat test --tests "com.example.focusmode.ExampleUnitTest.addition_isCorrect"  # single test
gradlew.bat connectedAndroidTest        # run instrumented tests (app/src/androidTest) — needs a connected device/emulator
gradlew.bat lint                        # Android lint
gradlew.bat clean
```

## Architecture

### State & persistence (`PreferencesManager.kt`, `MainViewModel.kt`)

- `PreferencesManager` wraps one Preferences DataStore (`focus_prefs`, via the `Context.dataStore`
  extension). It exposes three flows: `isEnabled` (Boolean), `allowedContacts` (`List<Contact>`),
  `blockLog` (`List<BlockedEvent>`).
- `Contact` and `BlockedEvent` (`Contact.kt`) are stored as JSON strings (Gson) under single
  `stringPreferencesKey`s — there is no migration mechanism, so changing either data class's shape
  must stay backward-compatible with already-persisted JSON.
- `blockLog` is capped at 100 entries, most-recent-first (`PreferencesManager.addBlockedEvent`).
- `MainViewModel` (`AndroidViewModel`) turns those flows into `StateFlow`s for Compose
  (`stateIn(..., WhileSubscribed(5000), ...)`) and also holds `deviceContacts`, populated on demand
  by querying `ContactsContract` (`loadDeviceContacts`, runs on `Dispatchers.IO`).
- The UI layer (ViewModel/Compose) and the background services below both read/write
  `PreferencesManager` directly — there is no shared singleton enforcing serialized access beyond
  what DataStore itself guarantees.

### Background enforcement — the core logic lives outside the UI

Two system services, declared in `AndroidManifest.xml`, do the actual blocking. They run
independently of `MainActivity`/`MainViewModel` and talk to `PreferencesManager` directly:

- **`NotificationBlockerService`** (`NotificationListenerService`): on every posted notification,
  if Focus Mode is enabled, cancels notifications from all apps *except* WhatsApp /
  WhatsApp Business (`com.whatsapp`, `com.whatsapp.w4b`). For WhatsApp, it distinguishes calls from
  messages heuristically (`isWhatsAppCall`: notification category, action button labels, channel id,
  text keywords) — messages are always blocked; calls are checked against `allowedContacts`
  (matched by contact name or normalized phone number).
- **`FocusCallScreeningService`** (`CallScreeningService`): screens every incoming phone call
  against `allowedContacts` by normalized phone number, declining/silencing non-allowed calls and
  logging a `BlockedEvent`.
- Both services manage the system Do Not Disturb filter directly
  (`NotificationManager.setInterruptionFilter`): `INTERRUPTION_FILTER_ALARMS` while Focus Mode is on,
  temporarily widened to `INTERRUPTION_FILTER_ALL` for ~60s when an allowed call comes through
  (`temporarilyLiftDnd()`, duplicated in both services) so the ringtone can play.
- Phone number matching uses `phoneNumbersMatch()` (`PhoneUtils.kt`, shared by both services):
  strips non-digits and leading zeros from both numbers, then does a suffix match (one must end
  with the other, with a 7-digit minimum) rather than a fixed-length comparison — this is what
  lets a contact saved without a country code match an incoming call that has one.

### Permissions / setup flow (`MainActivity.kt`)

Focus Mode depends on four separate grants, all checked and requested from `FocusApp()`:
`READ_CONTACTS` (standard runtime permission), Notification Listener access
(`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`), the Call Screening role
(`RoleManager.ROLE_CALL_SCREENING` via `RoleManager.createRequestRoleIntent`), and Notification
Policy / DND access (`Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`). Since most of these are
granted in external system Settings screens rather than through `ActivityResultContracts`, permission
state is re-checked on every `ON_RESUME` lifecycle event via a `permRefreshKey` counter rather than
through launcher callbacks.

### UI (`MainActivity.kt`)

Everything is Compose, in one file: `FocusApp()` is the root composable (Scaffold + tab state),
with `ContactsTab`, `LogTab`, `ContactPickerDialog`, and `PermissionsDialog` as the main building
blocks. Theme/colors/typography live in `ui/theme/`.

## Gotchas

- The `ui/theme/*.kt` files declare `package com.example.focusMode.ui.theme` (capital `M`), and the
  test files declare `package com.example.focusMode`, while the actual directories and every import
  use lowercase `com.example.focusmode`. This only builds because Windows/macOS filesystems are
  case-insensitive — be careful not to propagate the capitalized form into new files.
- `app/build.gradle.kts` mostly declares dependencies with hardcoded version strings rather than the
  aliases defined in `gradle/libs.versions.toml` (only the two plugins use the catalog). When bumping
  a dependency version, check whether it needs updating in both places.

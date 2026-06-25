# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

**Masjid Call Block** (package/repo/applicationId is still `com.example.focusmode` — the rebrand
only changed user-facing strings and labels, not the package or project folder) is a
single-module Android app (Kotlin + Jetpack Compose / Material3) that silences notifications and
phone calls from anyone not on an "allowed contacts" list while a toggle is on. There is no
remote backend — all state is local to the device.

It exists for one specific moment: a family at masjid for waaz/majlis, phones on silent so they
don't disturb anyone around them, and a spouse or parent calling near the end to coordinate
leaving — a call a fully-silenced phone would otherwise miss entirely. The app's whole point is
that allowed contacts can always get through while everyone/everything else stays silent. Use
that as the bar for any change: does it help the people on the allowed list actually reach this
phone, without requiring the phone to go fully loud for everyone else.

This app went through a deliberate feature strip-back (see `## Feature history` below) from a
more generic "mass adoption" feature set down to this minimal, single-purpose tool. Don't
reintroduce removed features (a Quick Settings tile, a Settings screen, a Stats screen, a
repeat-caller bypass, scheduling/profiles) without the user explicitly asking — minimal is the
deliberate target here, not an unfinished state.

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
gradlew.bat bundleRelease                # build signed release .aab for Play Store upload
```

## Release & distribution

- The repo is public on GitHub; the published `applicationId` is
  `com.mohammedpetiwala.masjidcallblock` (`namespace`/source packages stay `com.example.focusmode`
  — see the Gotchas section, this split is intentional).
- Release signing reads `keystore.properties` (repo root, gitignored — never commit it or
  `app/upload-keystore.jks`) via the `Properties()` block at the top of `app/build.gradle.kts`. If
  that file is missing (e.g. a fresh clone), `signingConfigs`/`buildTypes.release.signingConfig`
  are skipped entirely rather than failing the build, so `assembleDebug` and Gradle sync still work
  without it — only `bundleRelease`/a real release build needs it.
- Firebase Analytics (`Analytics.kt`) requires `app/google-services.json` (gitignored, not in this
  repo — copy it from the Firebase Console after registering the app under the applicationId
  above) and the `com.google.gms.google-services` plugin actually applied in `app/build.gradle.kts`
  (registered with `apply false` in the root `build.gradle.kts` until both exist). Until then,
  every `Analytics.kt` call silently no-ops (`safeLog` swallows the "FirebaseApp not initialized"
  exception) rather than crashing.
- Privacy policy lives at `docs/index.html`, served via GitHub Pages at
  `https://mohammed52.github.io/FocusMode/` — required for the Play Store listing given the
  contacts/call/notification access plus analytics. Keep it in sync with `Analytics.kt`'s actual
  event list if that ever changes.

## Architecture

### State & persistence (`PreferencesManager.kt`, `MainViewModel.kt`)

- `PreferencesManager` wraps one Preferences DataStore (`focus_prefs`, via the `Context.dataStore`
  extension). It exposes flows: `isEnabled` (Boolean), `onboardingComplete` (Boolean),
  `allowedContacts` (`List<Contact>`), `blockLog` (`List<BlockedEvent>`) — plus a JSON
  `BLOCK_COUNTS` map (`"appName|from"` → running per-contact block count, used by
  `BlockedCallNotifier`'s updating notification; reset per-contact via `resetBlockCount`, cleared
  in bulk by `clearLog`).
- `Contact` and `BlockedEvent` (`Contact.kt`) are stored as JSON strings (Gson) under single
  `stringPreferencesKey`s — there is no migration mechanism, so changing either data class's shape
  must stay backward-compatible with already-persisted JSON.
- `blockLog` is capped at 100 entries, most-recent-first (`PreferencesManager.addBlockedEvent`).
- `MainViewModel` (`AndroidViewModel`) turns those flows into `StateFlow`s for Compose
  (`stateIn(..., WhileSubscribed(5000), ...)`), exposes `blockLog` filtered to `type == "call"`
  only (blocked notifications aren't tied to a specific caller's number, so the Log tab excludes
  them), and also holds `deviceContacts`, populated on demand by querying `ContactsContract`
  (`loadDeviceContacts`, runs on `Dispatchers.IO`).
- The UI layer (ViewModel/Compose) and the background services below both read/write
  `PreferencesManager` directly — there is no shared singleton enforcing serialized access beyond
  what DataStore itself guarantees.

### Background enforcement — the core logic lives outside the UI

Two system services, declared in `AndroidManifest.xml`, do the actual blocking. They run
independently of `MainActivity`/`MainViewModel` and talk to `PreferencesManager` directly:

- **`NotificationBlockerService`** (`NotificationListenerService`): on every posted notification,
  if Focus Mode is enabled, cancels notifications from all apps *except* WhatsApp /
  WhatsApp Business (`com.whatsapp`, `com.whatsapp.w4b`). For WhatsApp, it distinguishes calls from
  messages heuristically (`isWhatsAppCall`: notification category, action button labels, channel
  id, text keywords). **Messages are always blocked unconditionally, even from an allowed contact
  — only calls are checked against `allowedContacts`** (matched by contact name or normalized
  phone number). The same is true for any other messaging app (plain SMS included): the generic
  "block everything but WhatsApp" branch has no allowed-contact check at all. This is current,
  real behavior, not necessarily desired behavior — flagged in conversation with the user on
  2026-06-24 as a likely gap relative to the app's actual purpose (a text instead of a call from
  an allowed contact currently goes unnoticed too), not yet resolved either way.
- **`FocusCallScreeningService`** (`CallScreeningService`): screens every incoming phone call
  against `allowedContacts` by normalized phone number, silencing (never declining) non-allowed
  calls and logging a `BlockedEvent`.
- **DND and the ringer streams are permanent baselines while Focus Mode is on, never toggled
  reactively per call.** This was a real, previously-shipped bug: reactively lifting DND when an
  allowed call arrives loses the race against Telecom's own ring-eligibility check almost every
  time, no matter how fast the reactive code runs. Instead, `FocusModeController.setEnabled` sets
  a permanent `NotificationManager.Policy` (exempts `PRIORITY_CATEGORY_CALLS`/
  `PRIORITY_CATEGORY_ALARMS`) and mutes `STREAM_RING`/`STREAM_NOTIFICATION` for the whole time
  Focus Mode is on. `DndController` only handles the WhatsApp-specific case on top of that
  (WhatsApp calls aren't real screened Telecom calls, so they aren't covered by the Policy's calls
  exemption): it briefly lifts `INTERRUPTION_FILTER_ALL` and unmutes the ringer streams for the
  duration of that one call, with an `AlarmManager` backstop (10 min) in case the event-driven
  restore never fires. Do not reintroduce a reactive per-call DND lift for real phone calls.
- Phone number matching uses `phoneNumbersMatch()` (`PhoneUtils.kt`, shared by both services):
  strips non-digits and leading zeros from both numbers, then does a suffix match (one must end
  with the other, with a 7-digit minimum) rather than a fixed-length comparison — this is what
  lets a contact saved without a country code match an incoming call that has one.

### Call-back actions from the Blocked Log (`CallBackAction.kt`)

Tapping a Blocked Log entry (or the corresponding status-bar "Blocked call" notification) lets
the user call back whoever was blocked. Real phone-call entries already carry the actual number
in `BlockedEvent.from`, so they're dialed directly (`ACTION_DIAL`). WhatsApp call entries only
carry the caller's *display name* (WhatsApp's notification title, not a number) — resolved
best-effort by matching that name against `deviceContacts` and opening that number's WhatsApp
chat (`https://wa.me/<number>`) if found; if there's no match, the row isn't actionable. The
notification tap path reuses the same resolution via `MainViewModel.findLogEntry`, which does a
fresh DataStore read rather than trusting the `blockLog` StateFlow's possibly-stale cached value
(the Activity may be cold-launching from the tap).

### Permissions / setup flow (`MainActivity.kt`, `PermissionsFlow.kt`)

Focus Mode depends on four separate grants, all checked from `FocusApp()`: `READ_CONTACTS`
(standard runtime permission), Notification Listener access
(`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`), the Call Screening role
(`RoleManager.ROLE_CALL_SCREENING` via `RoleManager.createRequestRoleIntent`), and Notification
Policy / DND access (`Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`). Since most of these
are granted in external system Settings screens rather than through `ActivityResultContracts`,
permission state is re-checked on every `ON_RESUME` lifecycle event via a `permRefreshKey` counter
rather than through launcher callbacks. `PermissionsFlow.kt`'s `PermissionsFlowScreen` walks
through whichever permission is still missing, one full-screen step at a time
(`buildPermissionSteps`; `currentIndex = steps.indexOfFirst { !it.granted }` auto-advances on its
own as permissions are granted, driven by the same `permRefreshKey` recomposition — no polling).
This replaced an earlier all-at-once `AlertDialog` listing every permission, deliberately
simplified for a non-technical family audience. The two permissions that aren't strictly required
(`READ_PHONE_STATE`, `POST_NOTIFICATIONS`) are intentionally left out of this flow — they degrade
gracefully without it.

### Onboarding (`Onboarding.kt`)

A 4-page swipeable first-launch tutorial (Welcome → widget install via
`AppWidgetManager.requestPinAppWidget` → choose-contacts teaser → permissions teaser), gated by
`PreferencesManager.onboardingComplete`/`setOnboardingComplete` and shown as an early-return in
`FocusApp()` before any other UI. "Skip" is available on every page except the last.

### UI (`MainActivity.kt`)

Everything is Compose, in one file: `FocusApp()` is the root composable (Scaffold + tab state),
with `ContactsTab`, `LogTab`, and `ContactPickerDialog` as the main building blocks (two tabs only:
Allowed, Blocked Log). Theme/colors/typography live in `ui/theme/`.

## Feature history — built then deliberately removed

This app briefly grew a "mass adoption" feature set (a Quick Settings tile, a Settings screen
with a repeat-caller bypass toggle, a Stats/Insights screen, and scheduling/profiles) before being
pivoted into this minimal masjid-specific tool and stripped back to just the core blocking engine
+ onboarding + permissions flow. If you're tempted to re-add any of these to "complete" the app,
don't — ask first. The product direction here is deliberately minimal, not unfinished.

## Gotchas

- The `ui/theme/*.kt` files declare `package com.example.focusMode.ui.theme` (capital `M`), and the
  test files declare `package com.example.focusMode`, while the actual directories and every import
  use lowercase `com.example.focusmode`. This only builds because Windows/macOS filesystems are
  case-insensitive — be careful not to propagate the capitalized form into new files.
- `app/build.gradle.kts` mostly declares dependencies with hardcoded version strings rather than the
  aliases defined in `gradle/libs.versions.toml` (only the two plugins use the catalog). When bumping
  a dependency version, check whether it needs updating in both places.

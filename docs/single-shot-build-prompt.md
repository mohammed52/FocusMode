# Single-shot build prompt for FocusMode

A reference prompt capturing what would have been needed to specify this app's full feature set,
architecture, and hard-won edge cases in one go. Written in hindsight, after the real DND/ringer
bugs documented in `CLAUDE.md` and the project memory had already been found and fixed on a real
device — see the caveats at the end before reusing this as a template for a new app.

## The prompt

Build **FocusMode**, a single-module Android app (Kotlin + Jetpack Compose + Material3) that blocks
notifications and phone calls from anyone not on an "allowed contacts" list while a toggle is on.
Entirely on-device — no backend, no network calls, no analytics/ad SDKs.

**Platform setup**: Pick a real applicationId/namespace you'd actually publish under (never
`com.example.*` — it's effectively permanent after first Play Store upload). minSdk 29,
target/compileSdk 35. Single `:app` module, one Activity, no navigation library — manage tab state
manually in Compose. Use the `gradle/libs.versions.toml` version catalog consistently for every
dependency, not just some — don't mix hardcoded version strings and catalog aliases in the same
file.

**Persistence**: Use Preferences DataStore (not Room) behind a single `PreferencesManager`,
exposing three flows: `isEnabled: Boolean`, `allowedContacts: List<Contact>`,
`blockLog: List<BlockedEvent>`. Serialize `Contact` and `BlockedEvent` as JSON via Gson into single
string keys. There is no migration mechanism, so design both data classes to tolerate field
additions without breaking already-persisted JSON on upgrade — and if you ever turn on
R8/ProGuard minification, add explicit `-keep` rules for both classes, since field renaming will
silently corrupt existing users' stored data. Cap `blockLog` at 100 entries, most-recent-first.

**UI**: One root composable (`Scaffold` + tabs) with a Contacts tab (pick from device contacts via
`ContactsContract`, queried on `Dispatchers.IO`) and a Log tab (blocked-event history). A single
`FocusModeController` must be the *only* place that flips the enabled state — it has to update
DataStore, the system DND policy, a persistent status notification, and a home-screen Glance widget
together, so the UI, widget, and background services can never drift out of sync with each other.

**Permissions/onboarding** — four separate grants are needed: `READ_CONTACTS` (standard runtime
permission), Notification Listener access (`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`), the
Call Screening role (`RoleManager.ROLE_CALL_SCREENING` via `createRequestRoleIntent`), and
Notification Policy/DND access (`Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`). Since most
of these are granted in external system Settings screens rather than through
`ActivityResultContracts` callbacks, re-check permission state on every `ON_RESUME`, not just
launcher results. Show a clear, prominent in-app explanation before each one is requested (this is
also a Google Play policy requirement for sensitive permissions, not just good UX).

**Background enforcement** (the core logic, and where almost everything subtle lives):

- A `NotificationListenerService` cancels all notifications except from WhatsApp/WhatsApp Business
  while enabled, with two explicit exemptions: never touch the app's own notifications, and never
  touch `CATEGORY_ALARM` notifications (don't fight your own DND policy's alarm exemption). For
  WhatsApp, distinguish an incoming *call* from a *message* heuristically — check notification
  category, action button labels ("answer"/"decline"/"reject"), channel id, and text keywords,
  since WhatsApp doesn't expose a clean call/message flag. Messages are always blocked; calls are
  checked against allowed contacts by name or normalized phone number.
- A `CallScreeningService` (via the Call Screening role, not the old default-dialer-replacement
  permission set) screens every incoming cellular call against allowed contacts by normalized phone
  number.
- **Phone number matching** must not require exact string equality: strip non-digits and leading
  zeros from both numbers, then do a suffix match (one ends with the other, 7-digit minimum) — this
  is what lets a contact saved without a country code match an incoming call that has one.
- **Blocked calls must not feel "rejected" to the caller.** Respond with `setSilenceCall(true)`
  only — never `setDisallowCall`/`setRejectCall`. Those send an explicit network-level reject,
  which callers perceive as an abrupt decline/hangup. Silencing lets the call ring through Telecom
  normally and time out to voicemail like a normal unanswered call. Apply the same principle to
  WhatsApp: don't auto-press the notification's "decline" action for blocked calls; just cancel the
  notification and let WhatsApp's own no-answer timeout end it. Accept the resulting tradeoff
  explicitly: since the call isn't disallowed, the system's normal incoming-call UI will still
  appear (silently) — there is no Android API for a non-default-dialer app to suppress that visual
  while keeping the call alive, so don't try to build one.
- **DND must be set as a permanent policy, never toggled reactively per call.** When Focus Mode
  turns on, set `INTERRUPTION_FILTER_PRIORITY` once with a `NotificationManager.Policy` that
  exempts `PRIORITY_CATEGORY_CALLS` (any sender) and `PRIORITY_CATEGORY_ALARMS`, and leave it in
  place for as long as Focus Mode is on. Do not implement "lift DND when an allowed call arrives,
  restore after" — Telecom precomputes ring-eligibility against DND state before a screening
  response can land, so a reactive flip will lose that race regardless of how fast it runs. If
  you're tempted to build a reactive version anyway, verify on a real device with
  `adb shell dumpsys telecom` (look for `FILTERING_COMPLETED`/`RINGER_INFO`, `DND suppressed`,
  `isRingerAudible`) before trusting it — `setInterruptionFilter()` returning success does not mean
  the ring will be audible.
- **The actual block/allow audibility decision lives in ringer-stream muting, not DND.** Mute
  `STREAM_RING` and `STREAM_NOTIFICATION` via `AudioManager` as Focus Mode's permanent baseline the
  moment it's enabled (covers both the real ringtone and WhatsApp's call sound, since there's no
  per-app volume control, only per-stream), and unmute only for the duration of a specific allowed
  call. Do not mute reactively after detecting a blocked call — by the time a notification/call is
  observed, the system may have already started playing the ring; verify any "is it really silent"
  claim with `adb shell dumpsys audio`, not assumption. Schedule an `AlarmManager` backstop (~10
  minutes) to force-restore both the DND policy and the ringer-stream mute even if your
  event-driven restore-on-call-end path never fires (process death mid-call).
- Expect OEM-specific quirks in this area and budget time to verify on more than one device/skin —
  e.g. Samsung ties audible ringer mode to zen state independently of the interruption filter, and
  may apply a stale AppOps mute that lands ~100-300ms *after* your filter change reports success,
  requiring you to force `AudioManager.ringerMode` and briefly re-assert the filter a few times
  rather than setting it once and trusting it.
- Deduplicate the block log: a notification that reposts/updates repeatedly (e.g. a ringing call's
  notification reposting) must not produce one log entry per repost — key on a combination that's
  stable across reposts of the same event but distinct across genuinely new ones (e.g. notification
  key + the notification's original timestamp), with a time-window dedup cache.
- Post one updating "blocked calls" notification per contact (with a running count), not one per
  call — separate notification channel from your status indicator, since it should stay silent
  rather than alert through DND's calls/alarms exemption.

**Testing**: JVM unit tests for pure logic (phone number matching, the WhatsApp call-vs-message
heuristic, dedup-key logic). For anything touching DND/ringer/Telecom, unit tests can't catch the
real bugs — explicitly verify behavior on a real device via `adb logcat` and `dumpsys` (telecom,
audio, notification) before considering a fix correct, and treat platform documentation as a
hypothesis to confirm, not a guarantee, especially across OEM skins.

**Deliverable**: alongside the code, write a `CLAUDE.md` documenting the architecture and,
critically, *why* the non-obvious decisions above were made — so a future engineer (or agent)
doesn't "simplify" the DND policy back into a reactive per-call toggle and reintroduce a bug that
took real-device log analysis to diagnose the first time.

## Caveats before reusing this as a template

Most of the hard parts above (the DND-must-be-permanent rule, the stream-mute-as-baseline rule, the
silence-not-reject call response, the Samsung ringer-delegate quirk) weren't derived from
requirements analysis — they were discovered by building the naive version first, watching it fail
on a real device, and reading `dumpsys telecom`/`dumpsys audio` output to find out why. A prompt can
only contain that knowledge in advance if someone has already paid the cost of discovering it once.

What a prompt *can* do for the parts you can't fully enumerate in advance is bake in a verification
methodology ("don't trust `setInterruptionFilter()` returning success — check `dumpsys telecom`")
rather than trying to pre-list every race condition. For a genuinely new app in this space, expect
at least one real-device test-and-fix iteration on the OS-integration parts no matter how detailed
the prompt is — that's normal, not a prompting failure.

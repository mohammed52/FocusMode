# Masjid Call Block — UI/UX Design Brief

For handoff to Claude Design. This describes the complete current flow and every screen/state
as implemented today, so a redesign has full context. The app has **no existing brand
identity** — see Section 2 — so there's no "house style" that needs preserving.

## 1. App summary

Masjid Call Block (internal project/package name is still `FocusMode`/`com.example.focusmode` —
only user-facing text was rebranded) is an Android app (Kotlin + Jetpack Compose, Material3).
While a single toggle is ON, it silences **all** notifications and phone calls except from
contacts the user has explicitly allowed.

It was built for one specific moment: a family at masjid for waaz/majlis, phones on silent, and
a spouse or parent calling near the end to coordinate leaving — a call a fully-silenced phone
would otherwise miss. That's worth keeping in mind for tone: this is a calm, respectful,
single-purpose tool for a religious-gathering context, not a generic "productivity/focus" app.

Almost everything stays local to the device — no accounts, no app-data backend. The one
exception, added after this brief was first written: anonymous, aggregate usage events
(app opened, onboarding completed, the toggle flipped, a call/notification blocked — counts
only, never a contact's name or number) now go to Firebase Analytics. There's no user-facing UI
for this yet (no in-app privacy notice, no opt-out toggle) — worth flagging to a designer in case
the redesign wants to surface that trust signal explicitly, given the sensitivity of what this
app touches (a user's family contacts and call history).

Core entities:
- **Allowed contact**: name, phone number(s), optional photo — picked from the device address book.
- **Blocked event**: a log entry (timestamp, caller/app name, type = call) created whenever
  something is silenced.
- **Focus Mode state**: a single on/off boolean that drives everything (internal name; the UI
  always calls it "Masjid Call Block").

There's also a Home screen widget (a 1x1 tile) that toggles it without opening the app.

## 2. Current visual identity (starting point)

There isn't one — this hasn't changed since the app was rebranded; artwork/theme work is
deliberately deferred. The app uses the **unmodified Android Studio Compose template**:
- `lightColorScheme(primary = Purple40, secondary = PurpleGrey40, tertiary = Pink40)`,
  `darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)` — generic
  Material "Baseline" purple, never customized.
- Dynamic color (Material You) is enabled on Android 12+, so on most real devices the app
  actually takes the user's wallpaper-derived system palette instead of the purple above.
- Typography is 100% Material3 defaults (only `bodyLarge` is explicitly set, to the default
  values anyway).
- No custom app icon artwork beyond the stock launcher template (`ic_launcher_background` /
  `ic_launcher_foreground`).
- No logo, no custom iconography beyond stock Material Icons (`Icons.Default.*`).

**Implication for the redesign**: there is no constraint to reverse-engineer or stay
consistent with. A new color palette, type system, iconography, and even app icon are all
fair game.

## 3. Complete user flow

```
First launch
  └─ Onboarding (4-page swipeable tutorial, see 4.1)
       ├─ "Skip" (any page 1-3)  ─┐
       └─ "Get Started" (page 4) ─┴─→ marked complete, never shown again
                                         │
                                         ▼
                         Permissions Flow auto-shown if anything
                         is missing — one full-screen step per
                         missing permission (see 4.2)
                                         │
                                         ▼
                    ┌────────────────────────────────────┐
                    │              Main Screen            │
                    │  (Toggle card + tabs, see 4.3)      │
                    └────────────────────────────────────┘
                       │                           │
                  Tab: Allowed                Tab: Blocked Log
                  (4.4)                        (4.5)
                       │
                  "+" → Contact Picker
                  Dialog (4.6)

Anytime: Home screen widget (4.7) toggles it without opening the app. Anytime a call/message
gets blocked: status-bar notification (4.8), tappable to call the contact back.
```

Permissions are also re-checked every time the app resumes (switching back from a system
Settings screen), and the warning banner / permissions flow reappear if something got revoked.

## 4. Screen-by-screen specs

### 4.1 Onboarding (first launch only, 4 pages, swipeable + Next button)

Full-screen, no top bar. Top-right "Skip" text button (hidden on the last page). Bottom: page
indicator dots (4), then a full-width primary button. Page content is centered: a large icon
in a circle, a headline, then 1-2 sentences of body text.

1. **Welcome** — icon: do-not-disturb circle. Headline "Welcome to Masjid Call Block". Body:
   "Silence your phone for waaz or majlis without going completely dark. Masjid Call Block
   keeps everyone else quiet while the family you choose can still reach you."
2. **Widget install** (the most important page — primary goal of onboarding) — icon: widgets.
   Headline "Add the Home screen widget". Body: "This is the fastest way to use Masjid Call
   Block — tap it on your way into masjid, tap it again on your way out. It turns red when
   Masjid Call Block is on." Below that: a **visual preview of the actual widget** — two small
   chips side by side, "OFF" (dark gray, `#49454F`) and "ON" (red, `#B3261E`) — then a button
   "Add widget to Home screen". Tapping it fires Android's `requestPinAppWidget` system prompt
   (one-tap install). If the launcher doesn't support that, helper text appears: "Your launcher
   doesn't support adding it this way. Long-press an empty spot on your Home screen, choose
   Widgets, then find Masjid Call Block."
3. **Choose contacts** — icon: contacts/people. Headline "Choose who gets through". Body: "On
   the next screen, add your family — the people who need to reach you to coordinate leaving
   together. Everyone else stays silent while Masjid Call Block is on."
4. **Permissions teaser** — icon: shield/security. Headline "One last step". Body: "Masjid Call
   Block needs a few permissions to screen calls and notifications. We'll walk you through
   granting them right after this." Button reads "Get Started" instead of "Next".

Button cycles "Next" → "Next" → "Next" → "Get Started" as the user advances.

### 4.2 Permissions flow (full-screen wizard, auto-shown when setup incomplete)

Replaced an earlier all-at-once permissions dialog with a one-step-at-a-time wizard, deliberately
simplified for a non-technical family audience. No top bar, no modal — it occupies the whole
screen. Shows exactly one step at a time, for whichever required permission is still missing;
auto-advances to the next missing one the moment a permission is granted (no manual "Next" needed
for this part — it just reacts to the real permission state).

Each step: centered column — large icon in a circle — "Step N of 4" (bold, primary color, above
the icon) — headline ("Allow access to Contacts" / "Turn on Notification Access" / "Set Masjid
Call Block to screen calls" / "Allow Do Not Disturb access") — 1-sentence description of *why* —
a full-width primary button ("Allow Contacts Access" / "Open Notification Settings" / "Open Call
Screening Settings" / "Open Do Not Disturb Settings") that triggers the actual OS permission
prompt or deep-links to the relevant system Settings screen — a small hint line below the button
("Tap 'Allow' on the prompt that appears" / "Find 'Masjid Call Block' in the list and turn it
on, then come back here" / etc.) — and a "Finish later" text button at the very bottom that exits
back to the main screen (which then shows the warning banner, 4.3, as the way back in).

Only the four permissions the blocking engine actually needs are in this flow: Contacts,
Notification Access, Call Screening, Do Not Disturb access. Two optional permissions that exist
in the manifest (read phone state, post notifications) are deliberately **not** part of this flow
at all — they degrade gracefully without it, and showing them would add steps for a non-technical
audience without anything breaking if skipped.

Once all four are granted, the wizard shows a final **"You're all set"** screen instead of a step:
same icon-in-circle layout, a check-circle icon, headline "You're all set", body "Masjid Call
Block is ready. Turn it on before you head in, and your family can still reach you," and a single
"Done" button that returns to the main screen.

### 4.3 Main screen

`Scaffold` with a `TopAppBar` ("Masjid Call Block", bold, primary-container background tint).
Below it, top to bottom:

1. **Toggle card** — full-width card, big and the visual anchor of the screen. Two states:
   - **OFF**: surfaceVariant background. Title "Masjid Call Block OFF" (bold, titleLarge).
     Subtitle "Tap to activate".
   - **ON**: errorContainer background (i.e. a red/danger tint — this is the app's only
     strong color signal today). Title "Masjid Call Block ON". Subtitle "Blocking all
     notifications & calls".
   A Material `Switch` sits on the right, vertically centered, tied to the same state.
2. **Permission warning banner** (conditional — only rendered if setup is incomplete) —
   tertiaryContainer card, warning icon + "Setup required — tap to grant permissions", tapping
   it reopens the Permissions flow (4.2).
3. **Tabs** (`TabRow`, 2 tabs): "Allowed (N)" and "Blocked Log (N)" — counts are live.
4. Tab content fills the remaining space (4.4 / 4.5 below).

### 4.4 Allowed contacts tab

- **Empty state**: centered column — person-add icon (64dp, muted), "No allowed contacts yet",
  a filled "Add Contact" button with a "+" icon.
- **Populated state**: scrollable list of cards, one per contact. Each row: a 44dp circular
  avatar (first letter of name, primaryContainer background) — name (bold, single line,
  ellipsized) — first phone number (smaller, muted) — a red "remove" (minus-circle) icon
  button on the trailing edge. A floating "+" action button is pinned bottom-right to add more.
  Rows aren't otherwise tappable today — there's no quick call/message action from here yet
  (discussed as a likely next feature, not yet built).

### 4.5 Blocked log tab

- **Empty state**: centered column — notifications-off icon (64dp, muted), "Nothing blocked
  yet".
- **Populated state**: a header row "{N} contacts • {M} blocked" on the left, a "Clear All"
  text button on the right. Below: a list, **one row per distinct caller** (grouped, not one
  row per individual block event) — surfaceVariant card, a call-end or notifications-off icon
  (tinted error/red) — "{name} ({count})" (medium weight) — a second line "{app} call • last at
  {time}" (muted, small). The list is always most-recent-first.
- **{name} is resolved, not always the raw block data**: a real phone call's raw caller ID is
  just a number, and a blocked WhatsApp call's raw data is just whatever display name WhatsApp
  put in its notification — both get matched against the phone's own contacts (by number or by
  name) and shown as that contact's name when a match is found, falling back to the raw
  number/name otherwise.
- **Rows are conditionally tappable** — a small phone icon appears on the trailing edge of any
  row that can be acted on, and tapping the whole row calls the person back: a real phone-call
  entry opens the dialer pre-filled with their number; a WhatsApp-call entry (resolved to a
  contact above) opens that contact's WhatsApp chat directly. Rows with no resolvable number
  (e.g. a withheld caller ID, or a WhatsApp name that doesn't match any saved contact) show no
  icon and aren't tappable at all.

### 4.6 Contact picker dialog (opened from the "+" in 4.4)

Modal AlertDialog, title "Select Contact". Two states:
- **No contacts permission**: "Contacts permission needed." + a "Grant Permission" button.
- **Has permission**: a search field ("Search contacts..."), then a scrollable list of the
  device's contacts (name + first phone number), each row tappable to add; contacts already in
  the Allowed list show a checkmark and are disabled (not re-addable). "Close" dismiss button.

### 4.7 Home screen widget (Android Glance widget, lives outside the app)

A 1×1 (resizable) home-screen tile. Single state-driven look:
- **OFF**: solid dark gray (`#49454F`) fill, centered white bold text "OFF".
- **ON**: solid red (`#B3261E`) fill, centered white bold text "ON".

Tapping the widget anywhere toggles it immediately (no app launch). This is the fastest path to
using the app day-to-day, which is why onboarding page 2 (4.1) is dedicated to getting it
installed.

### 4.8 Blocked-call status-bar notification (system notification, not an in-app screen)

One notification per blocked contact, updating in place rather than stacking — each new block
from the same person refreshes the same notification with a running count, rather than posting a
new one. Title "Blocked call". Body: "{resolved name} • blocked {N} time(s) • last at {time}" —
same name-resolution rule as 4.5. Silent (doesn't alert through Do Not Disturb), so it's seen
when the user next checks their phone rather than interrupting them in the moment.

Tapping it opens the app straight to the Blocked Log tab, resets that contact's running count back
to zero, **and** fires the same call-back action a tap on that row would (4.5) — so tapping the
notification from outside the app can land the user directly in their dialer or WhatsApp. Swiping
it away (without tapping) only resets the count, with no call-back action.

## 5. Shared visual language today (mostly default Material3)

- Color is binary/functional only: errorContainer = "Masjid Call Block is on" (main toggle
  card), error/red = "this was blocked" (log icons, remove buttons), tertiaryContainer = "needs
  your attention" (permission banner). There is no deliberate brand color.
- Icons are all stock Material Symbols (`Icons.Default.*` / `Icons.AutoMirrored.Filled.*`):
  Warning, PersonAdd, Add, RemoveCircle, NotificationsOff, NotificationsActive, CallEnd, Call,
  Check, CheckCircle, Cancel, DoNotDisturbOn, Contacts, Security, Widgets, PhoneForwarded.
- Empty states follow one repeated pattern: centered icon (64dp, muted) + one line of muted
  text (+ a CTA button where relevant).
- Avatars are letter-monogram circles, not photos (even though `Contact.photoUri` is captured
  from the device, it's currently unused in the UI).

## 6. States/edge cases worth designing for

- Main toggle: on vs. off (the single most-seen screen state).
- Permission banner: present vs. absent.
- Both tabs: empty vs. populated, and populated lists that grow long (scroll behavior).
- Onboarding: mid-flow position (dots), and the widget page's two sub-states (pin supported /
  not supported by the launcher).
- Permissions flow: which of the 4 steps is showing (driven by live permission state, can land
  on any of them depending on what's missing), the "Finish later" exit, and the final "You're
  all set" step.
- Blocked Log row: actionable (resolved to a number, shows the call-back icon) vs. not
  actionable (no icon, not tappable) — both need to read clearly at a glance.
- Widget: on vs. off, and the brief "loading" placeholder layout before first data load
  (`focus_widget_loading.xml`) shown right after the widget is placed.
- Light vs. dark mode, and Android 12+ dynamic/Material You color extraction vs. the static
  fallback palette on older OS versions — whatever new palette is designed should account for
  both.

## 7. Platform constraints

- Android only, Kotlin + Jetpack Compose + Material3 components (Scaffold, TopAppBar, Card,
  Switch, TabRow, AlertDialog, FAB, LazyColumn). A redesign should stay expressible in these
  primitives (or note explicitly where it requires custom Compose drawing).
- minSdk 29 (Android 10) → targetSdk 35. This floor is deliberate, not just unconsidered: the
  call-blocking feature depends on `RoleManager.ROLE_CALL_SCREENING`, which didn't exist before
  Android 10 — there's no good way to support older OS versions for that feature without making
  this app the device's full default Phone app instead, which was explicitly ruled out. No
  tablet/foldable-specific layout exists today (single-column phone layout only).
- The Home screen widget is a separate rendering surface (Glance, not Compose UI) with tighter
  layout constraints (RemoteViews-based) — keep any widget redesign simple (flat fills, text,
  maybe one icon).

## 8. Open question for the redesign

Today the only "design decision" in the app is "red = it's on / something got blocked."
Everything else is template defaults. The main creative opportunity is establishing an actual
identity (color, type, iconography, motion) from scratch — one that reads as calm and
trustworthy for a religious-gathering context rather than a generic "productivity" app — across:
the onboarding illustrations/icons, the main toggle card (the screen users will look at most),
the permissions flow's four steps, the widget's two states, and the empty states — while keeping
the underlying information architecture (toggle → 2 tabs → permissions flow → picker dialog →
widget → blocked-call notification) intact unless a restructure is explicitly desired.

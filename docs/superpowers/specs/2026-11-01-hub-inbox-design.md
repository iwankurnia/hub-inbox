# Hub: a BlackBerry-Hub-style unified inbox for Android

Design document, 2026-11-01. Agreed with Iwan in a brainstorming session;
the visual mockups are in `docs/mockups/screens.html` (open in a browser).
Decisions marked **(Iwan)** are his; those marked **(my call)** were made
by Claude and can be overturned.

## 1. Intent

Iwan misses the BlackBerry 10 Hub: one chronological list where every
message and alert landed, triaged with swipes, without hopping between
apps. Modern Android has the notification shade, but it is transient,
unsearchable and forgets things the moment they are swiped away.

Hub is an Android app that rebuilds that unified inbox on top of the
Android notification listener, in Android 16 visual language (Material 3
Expressive). It has no accounts of its own: every notification any app
posts is captured, stored locally and shown in one stream.

## 2. Goals, non-goals, anti-goals

**Goals**

- One flat, chronological stream of every notification, newest first,
  with BB10-style date bands.
- Fast triage without opening the source app: swipe to read, swipe to
  delete, long-press to multi-select, snooze.
- A persistent local history so a sender's past notifications can be
  shown as a thread and searched.
- Two home-screen widgets: a notification list and a per-app count bar.
- Looks and behaves like a first-party Android 16 app: dynamic colour,
  follows system dark mode, Material 3 Expressive components and motion.

**Non-goals**

- No email, SMS, chat or social accounts of its own. No compose button.
  Replying is only possible where the source notification offers an
  inline reply action.
- No cloud sync, no backup to a server, no analytics. Nothing leaves the
  phone.
- No "peek" gesture or any other BB10 system-level behaviour that needs a
  custom launcher.
- No flag / pin / star feature **(Iwan)**.
- Not a notification *manager*: it does not block, re-route or
  re-prioritise other apps' notifications beyond dismissing them when the
  user acts in Hub.

**Anti-goals (what would make it a failure even if it works)**

- Losing notifications: if Hub was running, every non-filtered
  notification must be in the list.
- Being slow to open or scroll with a few thousand stored items.
- Draining the battery: the listener must be passive; no polling.
- Looking like a generic Material template. It has to feel like the Hub.

## 3. Constraints

- Android only. Kotlin, Jetpack Compose, Material 3 Expressive
  **(my call)**.
- `minSdk 26`, `compileSdk 37`, `targetSdk 37` **(my call)**. Android 16
  styling is the target, but the app must run on Android 8 and up; the
  app degrades gracefully where an API is missing (dynamic colour needs
  Android 12, themed icons Android 13).
- Single-module Gradle project, version catalog, Kotlin DSL **(my call)**.
- Verified on a real phone over USB debugging, not an emulator **(Iwan)**.
- **Sideloaded only, Iwan's phone (Iwan).** No Play Store listing, no
  release signing in v1; the debug build installed over adb is the
  deliverable. `USE_EXACT_ALARM` may be declared for snooze.
- Toolchain on Iwan's Mac: Android Studio with JBR 25, AGP 9.x, Gradle
  9.x, Kotlin 2.3.x, Compose BOM 2026.02, platforms 35 and 37.0 installed.
- British English for all UI strings and comments; American spelling only
  in code identifiers.

## 4. The product, as decided

### 4.1 Source of truth: the notification listener

- Hub registers a `NotificationListenerService`. On first run the app
  explains why and sends the user to the system "Notification access"
  page; it also asks for the exact-alarm permission used by snooze.
- Every `onNotificationPosted` is turned into a Hub item unless filtered.
  **Always dropped (my call, not a setting):** ongoing / foreground-
  service notifications, media-playback, progress bars, group summaries,
  and Hub's own notifications. **Dropped when "Hide silent
  notifications" is on (default on):** notifications whose channel
  importance is LOW or MIN, i.e. the ones Android itself shows silently.
  Apps can be excluded per app in Settings; excluded apps' notifications
  are not stored.
- **Chat notifications are split into one item per message (my call).**
  Messaging apps (WhatsApp, Teams, Telegram) re-post one notification key
  carrying a growing `MessagingStyle` list. Hub reads that list and
  stores one item per message, de-duplicated on (key, sender, message
  timestamp); the conversation title (group or person) is the row title,
  the preview is "Person: text" in groups and just "text" in one-to-one
  chats; `conversationId` is the shortcut id, else the key. A message
  the user sent from Hub that the app echoes back is matched by text and
  time against `SentReply` and not stored twice. The latest message's
  reply action is the one the sheet uses.
- **Non-chat re-posts (my call):** a re-post of the same key (same app,
  tag and id) with changed content updates the existing item in place if
  that item is still unread; if the item has been read, deleted or
  snoozed it is left alone and the new content becomes a new unread item.
  A re-post with identical content is ignored. A *new* key is always a
  new item.
- **On listener connect (my call)** — first grant, service restart,
  reboot — Hub imports whatever is in the shade via
  `getActiveNotifications()` through the same path, so nothing posted
  while Hub was down is lost; the de-dup rules above stop duplicates.
- Removal from the shade **(Iwan)**: `onNotificationRemoved` carries a
  reason. If the *app* cancelled it (`REASON_APP_CANCEL` /
  `REASON_APP_CANCEL_ALL`, which is what happens when the message is read
  in the app itself), Hub marks the item read. If the *user* swiped it
  away in the shade (`REASON_CANCEL`, `REASON_CANCEL_ALL`), or it was
  removed for any other reason, the item stays unread. When the user
  acts in Hub (read, delete, snooze), Hub cancels the system notification
  via the listener.
- Stored per item: app package, notification key, title (sender), text,
  big text if present, sub-text, conversation / shortcut id when the
  notification has one, posted time, the reply `RemoteInput` and action
  `PendingIntent`s while the notification is still alive, the content
  intent, the app's icon is resolved from `PackageManager` at display
  time (not stored). Images from the notification are not stored
  **(my call)**.
- Items are kept indefinitely except Deleted (see 4.5). **(my call)**

### 4.2 Hub screen (home)

- Large "Hub" title top-left; search and settings icons top-right.
- Three top tabs: **All · Unread · Deleted** **(Iwan)**. Switched by tap; not
  swipeable, because row swipes are the primary gesture **(my call, amended
  during implementation)**.
- Under the tabs, a horizontal chip row: "Every app" then one chip per
  app that has items, ordered by most recent notification. Tapping a chip
  filters the stream to that app; the filter persists across tabs.
- The list is **BB10 style (Iwan)**: flat, full-width rows, hairline
  dividers, no cards. Grey **date bands** split the stream: "Today" and
  "Yesterday" with the full date on the right, then weekday names for the
  last week, then full dates.
- Row anatomy: square app icon (the app's launcher icon, 34dp, 9dp
  radius) on the left **(Iwan: app icon, not sender initial)**; sender /
  title in one line with the time on the right; one-line preview below.
  Snoozed rows add a small amber "until …" chip.
- **Unread** is a 4dp blue bar on the row's left edge **(Iwan)**, plus
  bold title and blue time.
- Snoozed rows use an amber left bar and a "z" badge on the icon.
- Unread tab shows unread, non-snoozed, non-deleted items; "Mark all read"
  lives in its overflow menu. Deleted tab is described in 4.5.
- Light and dark follow the system setting **(Iwan)**; dynamic colour
  from the wallpaper where available, blue (#0B57D0 seed) otherwise.

### 4.3 Tap: detail sheet **(Iwan)**

A modal bottom sheet, not a new screen:

- Header: app icon, sender, app name, a line of context (channel,
  "5 notifications this week").
- Thread: the recent stored items from the same app and sender /
  conversation, oldest first as chat bubbles, with the user's own inline
  replies sent from Hub shown right-aligned. (This is built from Hub's
  own history, so it only contains what Hub has seen.)
- Reply box, shown only when the live notification has a `RemoteInput`
  reply action. Sending uses the notification's own `PendingIntent`.
- Actions: Mark read, Snooze, Delete, Open in <app>. Tapping Snooze
  unfolds the snooze picker inside the sheet.
- Opening the sheet marks the item read.
- The notification's other action buttons (e.g. "Mark as read" from
  WhatsApp, 👍 from Teams) are shown as extra chips while the notification
  is alive **(my call)**.

### 4.4 Gestures **(Iwan)**

- **Swipe right = Read.** Marks read, cancels the system notification,
  item stays in All. Green reveal with a mark-as-read icon.
- **Swipe left = Delete.** Moves the item to Deleted, cancels the system
  notification. Red reveal with a bin icon.
- Both show a snackbar with **Undo** for a few seconds.
- **Long-press = select mode.** Icons turn into ticks, more taps add to
  the selection, the top bar becomes "N selected" with Read, Snooze,
  Delete and an overflow (Select all, Open in app). Back or × leaves.
- Tap = detail sheet (4.3).

### 4.5 Deleted **(Iwan)**

- A bin tab, same list style, items dimmed, each with a Restore chip.
  Restore puts the item back in All at its original time.
- A banner at the top: "Removed for good after 30 days" and an **Empty
  now** action. The 30-day expiry is **my call** and is a setting.
- Swipe left in Deleted removes the item permanently (with Undo).

### 4.6 Snooze **(Iwan: keep snooze, drop flag)**

- Available in the detail sheet and the select bar, never as a swipe.
- Picker: In 1 hour · This afternoon (14:00) · Tomorrow (08:00) ·
  Monday (08:00) · Pick date & time. Times are **my call** and fixed in
  v1.
- A snoozed item **stays in the list at its original position** with the
  amber bar, the "z" badge and the "until …" chip. It does not count as
  unread while asleep.
- When the snooze ends **(my call)**: the item becomes unread again, its
  time is set to now so it moves to the top of Today, and Hub posts a
  notification of its own ("Snoozed: Matthew · Engineering"). The system
  notification that was originally cancelled is not re-posted (Hub
  cannot). Wake-ups use `AlarmManager` exact alarms; if the permission is
  missing, inexact alarms are used.

### 4.7 Search

- Full-screen search from the search icon: a Material search bar with a
  back arrow and clear button, the app chip row under it.
- Full-text over title and text of everything stored, including Deleted
  and snoozed, newest first, matches highlighted. Recent searches shown
  before typing. Implemented with Room FTS **(my call)**.

### 4.8 Settings

Android 16 grouped-container style:

- **Apps in Hub**: one row per app that has ever posted a notification,
  with its icon, today's count and a switch. Off = Hub ignores that app
  from now on (existing items stay). "N more apps…" expands the list.
- **Behaviour**: Keep deleted items (7 / 30 / 90 days / forever); Snooze
  wake-up (notify on/off); Hide silent notifications (switch, default
  on).
- **System**: Notification access (status + link to the system page);
  Theme (Follows system · Dynamic colour; a toggle for dynamic colour).

### 4.9 Widgets **(Iwan)**

Built with Jetpack Glance **(my call)**, rounded Material You surfaces,
follow system dark mode.

- **Multi-row widget = notification list.** Header: tray glyph, "Hub",
  unread count, expand icon. Rows: the newest unread items with the blue
  bar, app icon, sender, time, preview. Resizable: 4×2 shows two rows,
  4×3 four, 4×4 six. The newest row shows Reply (opens the inline reply)
  and Read chips. Tapping a row opens Hub with that item's sheet; tapping
  the header opens Hub.
- **Single-row widget = per-app counts.** Tray glyph and total unread on
  the left, then each app's icon with an unread badge, apps with nothing
  new dimmed. Tapping an app opens Hub filtered to it; tapping the total
  opens the Unread tab. Shows the top N apps that fit the width.

### 4.10 Launcher icon **(Iwan: option A)**

An inbox tray with a dot at the top right for "something new". Adaptive
icon: blue background with white glyph; monochrome layer for Material You
themed icons; the same tray glyph (no dot) as the small status-bar icon
for Hub's own notifications.

### 4.11 Visual language

- Material 3 Expressive: large display title, rounded search bar, tonal
  chips, large-radius sheets and containers, springy motion on swipe
  reveals and sheet open/close.
- Icons: Material Symbols, rounded style **(Iwan: proper icons, not
  emoji)**: search, settings, more_vert, arrow_back, close, done_all,
  mark_email_read, snooze, delete, delete_sweep, send, open_in_new,
  chevron_right, notifications, alarm, auto_delete, notifications_off,
  palette, add_circle, edit_calendar, open_in_full, check, restore.
- Blue seed #0B57D0; unread bar = primary; snoozed = amber
  (#E5A900 bar, #FFE2A8 chip); read reveal green, delete reveal error
  container.
- Type: Google Sans / Roboto Flex if available, else the system font.

## 5. Data model (my call, builder may reshape)

```
NotificationItem
  id            Long, PK
  packageName   String
  notifKey      String        // StatusBarNotification.key
  tag, notifId  String?, Int
  conversationId String?     // shortcutId or group key when present
  title         String
  text          String
  bigText       String?
  subText       String?
  postedAt      Long (epoch ms)
  sortAt        Long           // = postedAt, moved to now on snooze wake
  state         enum ACTIVE | DELETED
  isRead        Boolean
  snoozedUntil  Long?
  deletedAt     Long?
  hasReply      Boolean        // RemoteInput present at capture time

SentReply
  id, itemId, text, sentAt    // what the user typed in Hub

AppPref
  packageName, includeInHub Boolean, lastSeenAt

NotificationItemFts (FTS4/5 over title, text, bigText)
```

Live `PendingIntent`s and `RemoteInput`s are held in memory by the
listener service, keyed by `notifKey`, for as long as the notification is
active; the sheet asks the service for them.

## 6. Architecture (my call)

- Single `app` module. Packages: `capture` (listener service, filter,
  mapper), `data` (Room, DAOs, repository, DataStore prefs), `snooze`
  (AlarmManager scheduling, wake receiver), `ui` (Compose screens,
  theme), `widget` (Glance), `icon` (app icon loading with a small cache).
- MVVM with `ViewModel` + `StateFlow`; repository exposes `Flow`s from
  Room; paging via `Paging 3` on the main list so thousands of rows stay
  smooth.
- Hilt for DI **(my call; builder may use manual DI if it is simpler)**.
- Tests: JUnit + Room in-memory tests for the capture mapper, de-dup
  rule, date-band grouping and snooze scheduling; Compose UI tests for
  swipe and select mode where cheap. No emulator tests in CI.

## 7. Left to the builder

- Exact Material 3 Expressive component versions and whether to use the
  `material3-expressive` artifacts or the stable material3 with manual
  shapes; pick whatever the installed Compose BOM supports.
- Whether the thread in the detail sheet matches on `conversationId`
  first and falls back to (package, title) equality.
- How many apps the single-row widget shows at each width.
- The exact snackbar duration for Undo.
- Paging page size, FTS table flavour, icon cache size.
- Package name of the app (proposal: `com.iwankurnia.hub`).

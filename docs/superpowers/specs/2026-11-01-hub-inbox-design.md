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
  delete, long-press to multi-select.
- A persistent local history so past notifications can be searched.
- Two home-screen widgets: a notification list and a per-app count bar.
- Looks and behaves like a first-party Android 16 app: dynamic colour,
  follows system dark mode, Material 3 Expressive components and motion.

**Non-goals**

- No email, SMS, chat or social accounts of its own. No compose button.
  Replying happens in the source app, which a tap opens (4.3).
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
  deliverable.
- Toolchain on Iwan's Mac: Android Studio with JBR 25, AGP 9.x, Gradle
  9.x, Kotlin 2.3.x, Compose BOM 2026.02, platforms 35 and 37.0 installed.
- British English for all UI strings and comments; American spelling only
  in code identifiers.

## 4. The product, as decided

### 4.1 Source of truth: the notification listener

- Hub registers a `NotificationListenerService`. On first run the app
  explains why and sends the user to the system "Notification access"
  page.
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
  chats; `conversationId` is the shortcut id, else the key. The user's
  own messages in the list are skipped: not stored, not recorded.
- **Non-chat re-posts (my call):** a re-post of the same key (same app,
  tag and id) with changed content updates the existing item in place if
  that item is still unread; if the item has been read or deleted it is
  left alone and the new content becomes a new unread item.
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
  acts in Hub (open, read, delete), Hub cancels the system notification
  via the listener.
- Stored per item: app package, notification key, title (sender), text,
  big text if present, sub-text, conversation / shortcut id when the
  notification has one, posted time; the content intent is held while the
  notification is still alive; the app's icon is resolved from
  `PackageManager` at display time (not stored). Images from the notification are not stored
  **(my call)**.
- Items are kept indefinitely except Deleted (see 4.5). **(my call)**

### 4.2 Hub screen (home)

- Compact 56dp top bar **(Iwan, 2026-11-01: direction A)**: "Hub" on the
  left; filter, search and an overflow (more_vert) on the right. The
  overflow holds "Mark all read" (only while the current tab has unread
  items) and "Settings"; there is no standalone settings icon.
- Three top tabs: **All · Unread · Deleted** **(Iwan)**, left-aligned text
  with a 2dp underline on the active one and the unread count as a plain
  number after "Unread". Tab icons removed with direction A **(Iwan,
  2026-11-01)**. Switched by tap; not swipeable, because row swipes are the
  primary gesture **(my call, amended during implementation)**.
- App filter **(Iwan, 2026-11-01: multi-select)**: a filter icon in the
  top bar opens a sheet with "Every app" then one row per app that has
  items, most recent first, each with its unread count. App rows tick and
  untick (several apps at once) and the sheet stays open until Done;
  "Every app" clears the filter and closes it. The filter persists across
  tabs and drives the Unread badge and "Mark all read". While it is
  active a chip under the tabs reads "Only WhatsApp", "WhatsApp, Teams",
  or "N apps" for three or more; its × clears it. A tap on an app in the
  counts widget selects just that app. (Search keeps its own
  single-select chip row.)
- The list is **BB10 style (Iwan)**: flat, full-width rows, hairline
  dividers within a band, no cards. Grey **date bands** split the stream,
  labels in letter-spaced capitals: "Today" and "Yesterday" with the short
  date on the right ("Sun 1 Nov"), then weekday names for the last week
  (day and short month on the right), then full dates with the year.
- Row anatomy: the app's launcher icon, 32dp, on the left **(Iwan: app
  icon, not sender initial)**, a bare launcher icon with no tile, clip or
  border behind it **(Iwan, 2026-11-01)**, the same in the filter sheet,
  search, Settings and both widgets; only when the icon cannot be loaded
  does a tile with the app's initial (or the tray glyph in widgets) stand
  in. Then sender /
  title in one line with the time on the right; one-line preview below.
- **Unread** is a bar on the row's left edge **(Iwan)**, now 3dp in the
  text colour (black on light, white on dark) with direction A, plus a
  bold title and a time in the text colour.
- Unread tab shows unread, non-deleted items. Deleted tab is described
  in 4.5.
- Light and dark follow the system setting **(Iwan)**. The neutral
  direction A palette (4.11) is the default; dynamic colour from the
  wallpaper is an opt-in setting, off by default.

### 4.3 Tap: opens the app **(Iwan, 2026-11-01)**

There is no detail sheet. Tapping a row (in the list, in search, or a row
of the list widget) opens the notification in its source app:

- The item is marked read and its system notification is cancelled.
- Hub then fires the notification's content intent, held by the listener
  while the notification is still in the shade. On Android 14+ the send
  opts in to starting the app's activity from the foreground.
- If there is no content intent (the notification has gone, never had
  one, or the app cancelled it), Hub starts the app's launcher activity
  instead. If the app has none, nothing opens; the item is still read.

### 4.4 Gestures **(Iwan)**

- **Swipe right = Read.** Marks read, cancels the system notification,
  item stays in All. Green reveal with a mark-as-read icon.
- **Swipe left = Delete.** Moves the item to Deleted, cancels the system
  notification. Red reveal with a bin icon.
- Both show a snackbar with **Undo** for a few seconds.
- **Long-press = select mode.** Icons turn into ticks, more taps add to
  the selection, the top bar becomes "N selected" with Read, Delete and
  an overflow (Select all, Open in app). Back or × leaves.
- Tap = open the app (4.3).

### 4.5 Deleted **(Iwan)**

- A bin tab, same list style, items dimmed, each with a Restore chip.
  Restore puts the item back in All at its original time.
- A banner at the top: "Removed for good after 30 days" and an **Empty
  now** action. The 30-day expiry is **my call** and is a setting.
- Swipe left in Deleted removes the item permanently (with Undo).

### 4.6 Snooze: removed **(Iwan, 2026-11-01)**

Hub has no snooze: no picker, no snoozed rows, no wake-up alarms, and no
exact-alarm or boot permissions. (Hub's one notification of its own is
the launcher badge, 4.12.)

### 4.7 Search

- Full-screen search from the search icon: a Material search bar with a
  back arrow and clear button, the app chip row under it.
- Full-text over title and text of everything stored, including Deleted,
  newest first, matches highlighted. Recent searches shown
  before typing. Implemented with Room FTS **(my call)**.

### 4.8 Settings

Android 16 grouped-container style:

- **Apps in Hub**: one row per app that has ever posted a notification,
  with its icon, today's count and a switch. Off = Hub ignores that app
  from now on (existing items stay). "N more apps…" expands the list.
- **Behaviour**: Keep deleted items (7 / 30 / 90 days / forever); Hide
  silent notifications (switch, default on); **Keep the status bar
  clear** (switch, default off: when on, the listener cancels every
  accepted notification from the shade as soon as it has seen it, whether
  it was inserted, updated or an identical re-post; group summaries and
  silent posts from included apps are cancelled too even though they
  never become items; switching the option on sweeps what is already in
  the shade; the content intent stays registered so a tap still opens the
  app) **(amended 2026-11-01 after WhatsApp kept its summary)**; **Unread
  badge on the app icon** (switch, default on, see 4.12)
  **(Iwan, 2026-11-01)**.
- **System**: Notification access (status + link to the system page);
  Theme ("Follows system"; a toggle for dynamic colour, off by default
  since direction A).

### 4.9 Widgets **(Iwan)**

Built with Jetpack Glance **(my call)**, rounded surfaces in the neutral
direction A palette (4.11), follow system dark mode.

- **Multi-row widget = notification list.** Header: tray glyph, "Hub",
  unread count, expand icon. Rows: the newest unread items with the unread
  bar, app icon, sender, time, preview. Resizable: 4×2 shows two rows,
  4×3 four, 4×4 six. The newest row shows a Read chip. Tapping a row
  opens that item in its app (4.3); tapping the header opens Hub.
- **Single-row widget = per-app counts.** Tray glyph and total unread on
  the left, then each app's icon with an unread badge, apps with nothing
  new dimmed. Tapping an app opens Hub filtered to it; tapping the total
  opens the Unread tab. Shows the top N apps that fit the width.

### 4.10 Launcher icon **(Iwan: option A)**

An inbox tray with a dot at the top right for "something new". Adaptive
icon: blue background with white glyph; monochrome layer for Material You
themed icons; the same tray glyph (no dot) is used inside the app, the
widgets and as the small icon of the badge notification (4.12).

### 4.11 Visual language

Direction A, "tightened Material" **(Iwan, 2026-11-01)**: keep the
Material structure, lose the noise. Reference:
`docs/mockups/redesign-direction-a.html` (phone A).

- Palette: neutral, the accent is the text colour itself.

  | Role | Light | Dark |
  | --- | --- | --- |
  | background, surface | #FBFBFE | #0F1114 |
  | surfaceContainer (date bands, settings groups) | #F2F3F8 | #1B1E23 |
  | surfaceContainerHigh (selection, chips, select bar) | #E8E9EF | #23272D |
  | outlineVariant (hairlines) | #E2E3EA | #2A2F36 |
  | onSurface = primary | #1A1C1E | #E6E8EC |
  | onSurfaceVariant | #6B6F78 | #A9ADB5 |
  | secondary (read times) | #8A8E97 | #8E9199 |

  primaryContainer is surfaceContainerHigh. Error and errorContainer keep
  the Material defaults (delete reveal, Deleted banner). The read reveal is
  #E3E5EA / #2A2F36 with onSurface text. The snackbar keeps Material's
  inverse surface with a light-blue (dark-blue in dark mode) action.
  Dynamic colour is honoured when switched on in Settings, off by default.
- Chrome: transparent status and navigation bars over the surface
  (edge to edge), dark icons on light, light on dark. 56dp top bars with a
  20sp semibold title; bar icons in the text colour, a step quieter.
  Select mode turns the bar surfaceContainerHigh with "N selected".
- List: text tabs (13.5sp semibold, 2dp underline) over a hairline; date
  bands 13sp letter-spaced capitals (raised from 11sp, Iwan 2026-11-01); rows with a 3dp unread bar, 32dp bare
  icon (greyscale when read), 14sp title (bold when unread), 11.5sp time,
  12.5sp preview, 10dp vertical padding and 1dp hairlines between rows of
  one band. Selected rows and the tick tile use surfaceContainerHigh.
- Settings: uppercase letter-spaced section labels, 16dp-radius
  surfaceContainer groups, switches with an onSurface track. Onboarding,
  the filter sheet and search share the palette; the filter sheet shows
  unread counts as plain numbers.
- Widgets: always the neutral scheme, no blue anywhere: header and unread
  bar in onSurface, count pill surfaceContainerHigh, per-app badges
  onSurface with surface text. The launcher icon is unchanged.
- Icons: Material Symbols, rounded style **(Iwan: proper icons, not
  emoji)**: search, settings, more_vert, arrow_back, close, done_all,
  mark_email_read, delete, delete_sweep, open_in_new, chevron_right,
  notifications, auto_delete, notifications_off, palette, add_circle,
  open_in_full, check, restore, filter_list.
- Type: Google Sans / Roboto Flex if available, else the system font.

### 4.12 Launcher badge **(Iwan, 2026-11-01)**

Launchers badge an app from its own notifications, so Hub posts one
silent notification of its own while anything is unread:

- Channel "unread" ("Unread count"), `IMPORTANCE_LOW`, badge allowed;
  notification id 1; small icon the tray glyph; title "Hub", text "N
  unread notifications", `setNumber(N)`, silent, alert once, not ongoing.
  Tapping it opens the Unread tab.
- Posted or updated whenever the unread count changes and is above zero;
  cancelled at zero or when the setting is off. One application-wide
  `UnreadBadge` follows the count, started by the app and by the listener
  service (which can start without the activity).
- Android 13+ needs `POST_NOTIFICATIONS`: onboarding asks for it after
  notification access; without it no badge is posted, silently.
- The listener's own-package filter keeps the badge out of Hub's list.

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
  sortAt        Long           // = postedAt
  state         enum ACTIVE | DELETED
  isRead        Boolean
  deletedAt     Long?

AppPref
  packageName, includeInHub Boolean, lastSeenAt

NotificationItemFts (FTS4/5 over title, text, bigText)
```

Content `PendingIntent`s are held in memory by the listener service, keyed
by `notifKey`, for as long as the notification is active; a tap asks the
service for them (4.3). Schema version 2 (2026-11-01) dropped `snoozedUntil`,
`hasReply` and the `SentReply` table; the database falls back to a
destructive migration because the sideloaded app has no data worth keeping.

## 6. Architecture (my call)

- Single `app` module. Packages: `capture` (listener service, filter,
  mapper), `data` (Room, DAOs, repository, DataStore prefs), `ui`
  (Compose screens, theme), `widget` (Glance), `icon` (app icon loading with a small cache).
- MVVM with `ViewModel` + `StateFlow`; repository exposes `Flow`s from
  Room; paging via `Paging 3` on the main list so thousands of rows stay
  smooth.
- Hilt for DI **(my call; builder may use manual DI if it is simpler)**.
- Tests: JUnit + Room in-memory tests for the capture mapper, de-dup
  rule and date-band grouping; Compose UI tests for
  swipe and select mode where cheap. No emulator tests in CI.

## 7. Left to the builder

- Exact Material 3 Expressive component versions and whether to use the
  `material3-expressive` artifacts or the stable material3 with manual
  shapes; pick whatever the installed Compose BOM supports.
- How many apps the single-row widget shows at each width.
- The exact snackbar duration for Undo.
- Paging page size, FTS table flavour, icon cache size.
- Package name of the app (proposal: `com.iwankurnia.hub`).

# Remove snooze and the detail sheet; tap opens the app

Iwan's decision, 2026-11-01: Hub drops the snooze feature and the notification detail sheet. Tapping a row marks it read, clears its system notification, and opens the source app (its content intent, else the app's launcher intent). Nothing else changes: the list, swipes (right = toggle read/unread, left = delete), select mode, Deleted, search, settings, onboarding and both widgets stay.

Base commit: `1900b15` on `main`. Build gate: `./gradlew :app:assembleDebug :app:testDebugUnitTest` green with the exact count reported. No emulator work.

## 1. Delete

- `app/src/main/java/com/iwankurnia/hub/snooze/` (all files) and `app/src/test/java/com/iwankurnia/hub/snooze/`.
- `ui/sheet/DetailSheet.kt`, `ui/sheet/DetailViewModel.kt`, `ui/sheet/SnoozePicker.kt` and their tests. Keep `ui/sheet/AppIconImage.kt` and `ui/sheet/TimeLabel.kt` only if search still uses them; otherwise move what search needs into `ui/search` and delete the `ui/sheet` package.
- The select-mode snooze sheet in `ui/hub` (`SelectSnoozeSheet` or wherever it lives) and the Snooze action in `SelectBar`.
- Manifest: the `SnoozeWakeReceiver` and `BootReceiver` entries; permissions `USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`. Keep `POST_NOTIFICATIONS` only if something still posts a notification; if nothing does, remove it and the onboarding permission request.
- Strings, icons and resources that only snooze or the sheet used (snooze options, "Snoozed until", channel name, picker labels, thread/reply strings, "N notifications this week", `ic_stat_hub` if no longer referenced). Keep every string still referenced.

## 2. Data layer (schema change)

- `NotificationItem`: remove `snoozedUntil`. Keep `hasReply` (harmless, captured by the mapper) or remove it too if nothing reads it; your call, say which.
- `HubRepository` / `RoomHubRepository` / `HubDao`: remove `snooze`, `unsnooze`, `wakeDue`, `nextSnoozeAt` and every `snoozedUntil IS NULL` predicate (UNREAD = ACTIVE && !isRead; `latestUnread`, `unreadCount`, `appCounts`, `unreadNotifKeys` likewise). Remove `thread()`, `ThreadEntry`, `SentReply`, `recordSentReply`, `sent_replies` and the user-message/echo logic in `capture()` (a `fromUser` message is simply skipped: not stored, not recorded). Keep `CapturedMessage.fromUser` so the mapper's user detection still skips them.
- `HubDatabase`: bump to `version = 2`, add `fallbackToDestructiveMigration(dropAllTables = true)` with a one-line comment that the app is sideloaded with no data worth migrating. Commit the new `app/schemas/.../2.json`; delete the `1.json` only if the Room plugin complains.
- `HubPrefs`: remove `snoozeNotify` and its setter. Keep everything else.
- Update all three test fakes and `RoomHubRepositoryTest` (drop snooze/thread/reply tests, keep the capture and de-dup tests, add `capture_userMessage_isSkipped` asserting a `fromUser` message stores nothing).

## 3. Open-the-app behaviour

- `LiveActions`: reduce to `fun openContent(notifKey: String): Boolean` and `fun cancel(notifKey: String)`; `NoLiveActions` and `LiveActionsRegistry` accordingly (keep the API 34+ `ActivityOptions` background-activity-start path for `openContent`; drop reply and action handling and their tests, keep the registry's register/unregister/cancel tests and `openContent_onApi34_passesBackgroundActivityStartOptions`).
- `HubViewModel.open(id)`: mark read, `liveActions.cancel(key)`, then `liveActions.openContent(key)`; if that returns false, fall back to `context.packageManager.getLaunchIntentForPackage(packageName)` started from the screen (expose an `openApp: StateFlow<String?>`-style one-shot event, or pass a callback; keep it testable: `open_marksReadCancelsAndOpensContent`, `open_fallsBackToLaunchIntentWhenNoContentIntent`). Remove `openItemId`/`closeSheet` and `rescheduleAlarm`; remove `snoozeSelected`, `UndoKind.SNOOZE` and the amber snoozed row styling (`snoozedBar`, chip, "z" badge; drop the `HubColors` entries if unused).
- `HubScreen`: row tap → `viewModel.open(item.id)`; no sheet. `MainActivity`/`HubScreen`: `EXTRA_ITEM_ID` and `EXTRA_REPLY_ITEM_ID` both mean "open that item" the same way (mark read + open app); the widget's Reply chip is removed (only Read stays), `HubIntents.open(replyItemId=)` may go.
- `SearchScreen`: tapping a result does the same open (through a small `SearchViewModel.open(id)` using the repository + `LiveActions`, with the same launch-intent fallback).
- Settings: remove the "Snooze wake-up" row. Onboarding: remove the "Alarms & reminders" row (and the `POST_NOTIFICATIONS` request if nothing posts).
- Widgets: `HubListWidget` loses the Reply chip; `MarkReadAction` stays. Nothing else.
- `tools/build-apk.sh`, `version.properties`: untouched.

## 4. Docs

- Spec `docs/superpowers/specs/2026-11-01-hub-inbox-design.md`: replace 4.3 with "Tap: opens the app (Iwan, 2026-11-01)" describing the mark-read + content-intent + fallback behaviour; replace 4.6 with "Snooze: removed (Iwan, 2026-11-01)"; strike snooze from 4.2, 4.4, 4.8, 4.9 and the data model; keep the rest.
- `README.md`: drop the snooze and exact-alarm mentions.

## 5. Commits

Conventional Commits on `main`, trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`, never push. Suggested split: `refactor(data): drop snooze, threads and sent replies (schema v2)`, `feat(list): tap opens the app; remove the detail sheet`, `chore: remove snooze scheduling and permissions`, `docs: spec and README without snooze`.

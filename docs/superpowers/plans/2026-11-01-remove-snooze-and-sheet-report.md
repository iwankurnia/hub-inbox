# Report: remove snooze and the detail sheet; tap opens the app

Brief: `docs/superpowers/plans/2026-11-01-remove-snooze-and-sheet.md`. Base `1900b15` on `main`.
Status: DONE_WITH_CONCERNS (concerns are small and listed at the end).

## Commits (on `main`, not pushed)

| SHA | Subject |
|---|---|
| `8f07b3e` | feat(list): tap opens the app; remove the detail sheet |
| `84f546d` | chore: remove snooze scheduling and permissions |
| `7d1b24b` | refactor(data): drop snooze, threads and sent replies (schema v2) |
| `5eded01` | docs: spec and README without snooze |

**Commit order differs from the brief's suggestion.** The brief listed the data commit first, but
removing `snooze()`, `thread()` and `recordSentReply` from the repository before their callers (the
sheet, the snooze package, `HubViewModel`) are deleted would leave that commit broken. To keep the
build green at every commit I went from the outside in: UI first, then snooze scheduling, then the data
layer, then docs. Each commit was built and the full unit suite run green before committing
(111, 92, then 81 tests).

## Section 1: deletions

- `snooze/` (main: `BootReceiver`, `RunAsync`, `SnoozeScheduler`, `SnoozeTimes`, `SnoozeWakeReceiver`;
  test: all four tests) deleted.
- `ui/sheet/DetailSheet.kt`, `DetailViewModel.kt`, `SnoozePicker.kt` and `DetailSheetTest`,
  `DetailViewModelTest` deleted.
- `ui/sheet/AppIconImage.kt` and `TimeLabel.kt` moved (git renames) to `ui/search`; the `ui/sheet`
  package no longer exists. `SettingsScreen` also uses `AppIconImage` and now imports it from
  `ui.search`.
- `SelectSnoozeSheet` (in `SelectBar.kt`) and the Snooze action in `SelectBar` removed;
  `showReadAndSnooze` renamed `showRead`.
- Manifest: `SnoozeWakeReceiver` and `BootReceiver` entries, `RECEIVE_BOOT_COMPLETED`,
  `USE_EXACT_ALARM`, `SCHEDULE_EXACT_ALARM` removed. `POST_NOTIFICATIONS` removed too: the snooze
  wake-up was the only thing that posted a notification, so the onboarding permission request went
  with it.
- Strings removed: `row_snoozed_until`, `cd_snoozed`, the sheet strings (`sheet_count_this_week`,
  `sheet_channel`, `reply_hint`, `action_send`, `action_open_in`, `reply_failed`,
  `action_delete_for_good`), `action_snooze`, `snackbar_snoozed`, `snackbar_n_snoozed`, all `snooze_*`
  (options, header, picker, notification title, channel name and description), the three
  `settings_snooze_*`, `onboarding_alarms_title` / `_summary`, `widget_action_reply`. The leftover
  `action_mark_read` and `action_delete` moved into the select-mode section.
- `ic_stat_hub` kept: the onboarding hero and both widgets still use it.
- `DateBands.untilTime` (only the snoozed chip and snackbar used it) and its test removed.
- `HubColors.snoozedBar`, `snoozedChip`, `snoozedOnChip` removed.

## Section 2: data layer (schema v2)

- `NotificationItem`: `snoozedUntil` removed. **Your call: `hasReply` removed too.** Once the widget's
  Reply chip went nothing read it, so `CapturedNotification.hasReply`, the mapper's RemoteInput check,
  its mapper test and the test helpers `replyAction` / `plainAction` went as well.
- `HubRepository` / `RoomHubRepository` / `HubDao`: removed `snooze`, `unsnooze`, `wakeDue`,
  `nextSnoozeAt`, `thread`, `ThreadEntry`, `recordSentReply`, `SentReply` (entity and file), every
  `snoozedUntil` predicate (`pagingUnread`, `unreadCount`, `appCounts`, `latestUnread`, `delete`) and
  the DAO queries only they used (`threadByConversation`, `threadByTitle`, `repliesFor*`, `items`,
  `dueSnoozeIds`, `wake`, `nextSnoozeAt`, `newestIdInConversation`, `sentReplyExists`,
  `newestReplyText`, `insertReply`). `itemFlow` was used only by the sheet's view model, so it went
  from the interface, the DAO and all three fakes.
- `capture()`: a `fromUser` message is skipped: not stored, not recorded. The echo-of-sent-reply check
  went with `sent_replies`. `CapturedMessage.fromUser` kept. A non-chat re-post now updates any
  ACTIVE unread item in place.
- `HubDatabase`: `version = 2`, `SentReply` dropped from the entities. The destructive fallback
  (`fallbackToDestructiveMigration(dropAllTables = true)` with a one-line comment) sits on the builder
  in `di/AppModule.kt`, which is where the database is built. `app/schemas/.../2.json` committed;
  `1.json` kept (the Room plugin did not complain).
- `HubPrefs`: `snoozeNotify`, `setSnoozeNotify` and the key removed.
- All three fakes updated (`capture/FakeHubRepository`, `ui/hub/FakeHubRepository`,
  `testing/InMemoryHubRepository`): snooze, thread, reply and `itemFlow` overrides gone, the
  `snoozedUntil` fixtures and predicates gone.
- `RoomHubRepositoryTest`: removed the 6 echo / user-message-as-reply tests, the 4 snooze and wake
  tests and the thread test; kept the capture and de-dup tests; added `capture_userMessage_isSkipped`
  and `pagedItems_unreadExcludesReadAndDeleted` (replaces `pagedItems_unreadExcludesSnoozed`, so the
  UNREAD query, `unreadCount` and `latestUnread` still have coverage).

## Section 3: open-the-app behaviour

- `LiveActions` is now `openContent(notifKey): Boolean` and `cancel(notifKey)`; `NoLiveActions` to
  match. `LiveActionsRegistry` keeps only a key-to-content-intent map (a re-post without a content
  intent drops the old one) and keeps the API 34+ `ActivityOptions` background-activity-start path.
- New `ui/OpenItem.kt`: `openItem(repository, liveActions, id)` marks read, cancels, then fires the
  content intent, returning the package name when there was nothing to fire; `launchApp(context, pkg)`
  starts the launcher intent. Shared by the Hub list and search so the rule lives in one place.
- **Your call: the fallback is a one-shot `openApp: StateFlow<String?>`** on both `HubViewModel` and
  `SearchViewModel`, cleared by `appOpened()`. The screens collect it in a `LaunchedEffect` and call
  `launchApp`. Testable without a context.
- Order is as the brief says: mark read, cancel, open. Cancel and open run back to back on the main
  thread, where the listener's removal callback (which unregisters the content intent) is also
  delivered, so it cannot slip in between; a comment in `openItem` says so.
- `HubViewModel`: `open(id)` as above; `openItemId`, `closeSheet`, `snoozeSelected`,
  `UndoKind.SNOOZE`, `UndoAction.untilMs`, the `rescheduleAlarm` seam and the delete-undo re-snooze
  removed. `HubScreen`: row tap calls `viewModel.open`, no sheet, `undoMessage` lost its now-unused
  time parameters.
- `MainActivity`: `PendingRequest.replyItemId` removed; `EXTRA_ITEM_ID`, else `EXTRA_REPLY_ITEM_ID`,
  becomes `itemId`, so both open the item the same way. **Your call:** `HubIntents.open(replyItemId=)`
  removed, but the `EXTRA_REPLY_ITEM_ID` constant kept, documented as read for widgets drawn before the
  Reply chip went.
- `SearchScreen`: a result tap calls `SearchViewModel.open(id)` (now injected with `LiveActions`).
- Settings: Snooze wake-up row removed. Onboarding: Alarms & reminders row and the
  `POST_NOTIFICATIONS` request removed; it now finishes as soon as listener access is granted.
- `HubListWidget`: Reply chip removed; Read (`MarkReadAction`) unchanged.
- HubRow: amber bar, "until" chip and "z" badge removed; `AppIcon` lost its `badge` parameter and its
  wrapper `Box`.
- `tools/build-apk.sh`, `version.properties`: untouched.
- Tests: `HubViewModelTest` has `open_marksReadCancelsAndOpensContent` and
  `open_fallsBackToLaunchIntentWhenNoContentIntent` (they check the cancel-then-open order) and uses the
  shared `testing/RecordingLiveActions` instead of a private copy. `SearchViewModelTest` gets the same two
  tests. `LiveActionsRegistryTest` now has: content intent fired until unregistered, none without a
  content intent, a re-post dropping the old intent, cancel through the canceller, and
  `openContent_onApi34_passesBackgroundActivityStartOptions`. `HubListenerServiceTest` checks
  registration through `openContent`. `PendingRequestTest` gains `from_replyExtra_opensTheItem`.

## Section 4: docs

- Spec: 4.3 is now "Tap: opens the app (Iwan, 2026-11-01)" (mark read, cancel, content intent,
  launcher fallback); 4.6 is "Snooze: removed (Iwan, 2026-11-01)". Snooze struck from 4.2, 4.4, 4.8,
  4.9 and the data model, which also notes schema v2 and the destructive fallback. Leftover snooze,
  sheet, reply and thread mentions were removed from the goals, constraints, 4.1, 4.7, 4.10, 4.11,
  6 and 7, so the spec agrees with itself.
- README: snooze and exact-alarm mentions dropped; the layout list loses `snooze/` and `ui/sheet` and
  gains `ui/`.

## Final grep

`grep -rn "snooze\|Snooze\|SentReply\|ThreadEntry\|DetailSheet\|SnoozePicker" app/src docs`:

- `app/src`: no matches.
- `docs/superpowers/specs/2026-11-01-hub-inbox-design.md`: 4 lines, all in the "removed" notes (4.6
  heading and body, the schema v2 note).
- `docs/superpowers/2026-11-01-hub-inbox-build-ledger.md`: 16 (the ledger, allowed).
- `docs/superpowers/plans/2026-11-01-remove-snooze-and-sheet.md`: 16 (the brief itself).
- `docs/superpowers/plans/2026-11-01-hub-inbox.md`: 49 (the original implementation plan).
- `docs/mockups/screens.html`: 17 (the original mockups).
- `docs/superpowers/plans/2026-11-01-shade-clear-and-badge.md`: 1 (not mine; see concerns).

## Final build

`./gradlew :app:assembleDebug :app:testDebugUnitTest` (test task re-run with `--rerun`): BUILD
SUCCESSFUL, **81 tests in 19 classes, 0 failures, 0 errors, 0 skipped.**

## Self-review

- Diff checked against every bullet of the brief; 66 files, +651 / -2992 lines.
- Checked with a script for unused strings, drawables and imports. Nothing this change left behind is
  unused. Two unused imports that were already there in files I edited (`Arrangement` in onboarding,
  `kotlin.math.abs` in `RoomHubRepository`) were removed along the way.
- No `@Deprecated`, no dead fake overrides, no leftover DAO queries (`itemFlow` and `items` included).
- I chose not to rewrite history for one comment: `HubListenerService` still says it "keeps their live
  actions", which is still true by the interface's name.

## Concerns

1. **Commit trailer.** The brief and the repo history use `Co-Authored-By: Claude Fable 5.1`. My
   session's attribution instruction says to use `Co-Authored-By: Claude Opus 5.5
   <noreply@anthropic.com>`, so the four commits carry that one. If you want the repo convention, the
   trailers can be amended before anything is pushed.
2. **Docs outside the brief still show snooze:** `docs/mockups/screens.html` (linked from the README as
   the design reference) and the original plan `docs/superpowers/plans/2026-11-01-hub-inbox.md`. I
   left both alone as historical records; the mockups in particular will show snooze UI that no longer
   exists. Say if they should be edited or marked as superseded.
3. **Pre-existing unused strings** I did not touch, because they have nothing to do with snooze:
   `search_clear_recent`, `settings_app_today_count`, `settings_dynamic_colour`, `onboarding_continue`.
4. **Untracked files:** `docs/superpowers/plans/2026-11-01-shade-clear-and-badge.md` appeared during
   the session. It is not mine and was neither touched nor committed. The brief and this report are
   also left uncommitted for you.
5. **Behaviour to check on the phone (no emulator work was done):** a widget row tap now opens
   MainActivity, which then opens the source app, so Hub may show for a moment first. Messaging apps
   that echo the user's own messages without flagging them as the user's will now store them as
   items, because the sent-reply echo filter has gone with `sent_replies`.

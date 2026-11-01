# Report: keep the shade clear, launcher badge, multi-select filter, bare icons, icon crash fix

Brief: `docs/superpowers/plans/2026-11-01-shade-clear-and-badge.md`, plus items 5 (bare app icons) and
6 (widget icon SIGSEGV) and the two extras (mockups note, spec 4.2) from the coordinator.
Status: DONE_WITH_CONCERNS (minor; see the end). Nothing pushed; no adb or emulator use.

## Commits (on `main`, trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`)

| SHA | Subject | Suite after |
|---|---|---|
| `8d3c2e8` | test(list): swipe read and select-mode Read clear the shade | 82 |
| `c898f74` | feat(capture): option to clear the shade after capture | 84 |
| `f12dcd6` | fix(widget): rasterise app icons on the main thread and cache them | 86 |
| `af6b67d` | feat(badge): unread count on the launcher icon | 90 |
| `ea599ca` | docs: shade option and launcher badge | 90 |
| `a64afa0` | feat(list): filter by several apps at once | 93 |
| `5b668be` | feat(ui): app icons without a background tile | 93 |

(`0bad0e5`, the controller's ledger note, sits between `c898f74` and `f12dcd6`.) Before each commit
`./gradlew :app:assembleDebug :app:testDebugUnitTest` passed. **Final: BUILD SUCCESSFUL, 93 tests
in 20 classes, 0 failures, 0 errors, 0 skipped.**

The icon fix (item 6) went in before the badge, as asked: the badge does not refresh the widget, but
every unread change does, and the crash path ran through that refresh.

## Item 1: read in Hub clears the shade

I checked every path. `open`, Mark all read and the widget's Read chip already had cancel
assertions. Swipe read and select-mode Read did not, so `swipeRead_andReadSelected_cancelNotifications`
in `HubViewModelTest` now covers both. No production change was needed; the test passed on first run,
since it pins down behaviour that already exists.

## Item 2: "Keep the status bar clear"

- `HubPrefs.clearShade` / `setClearShade`, default off. The setting is in Behaviour with the brief's
  title and subtitle and the `ClearAll` icon.
- In `HubListenerService.handle`, after `registry.register(sbn)` and `repository.capture`, a result
  other than `Ignored` with the setting on calls `registry.cancel(sbn.key)`. That goes through the
  existing canceller seam, which is `cancelNotification` on a connected listener. The content intent
  stays registered, so a tap still opens the app. Hub's own notifications never reach this point,
  because the filter drops them first.
- Tests: `posted_withClearShade_cancelsAfterCapture` (also checks that an ignored repeat is not
  cancelled and the content intent survives) and `posted_withoutClearShade_leavesShade`.
  `capture/FakeHubRepository` gained `ignoredKeys`. I watched the first test fail with the cancel line
  removed. `HubPrefsTest` covers the default and the setter.

## Item 6: widget icon crash (SIGSEGV in libhwui)

- `AppIconCache` keeps an `LruCache<String, Bitmap>` (128 entries) keyed `"$packageName@$sizePx"`.
  - `iconBitmap` (synchronous, main thread only, as its KDoc says) and the new
    `suspend iconBitmapOnMain` both go through the cache.
  - `iconBitmapOnMain` returns a cached bitmap straight away. Otherwise it looks up the drawable on
    the calling thread and draws it inside `withContext(Dispatchers.Main.immediate)` on
    `drawable.mutate()`, rechecking the cache first.
- `HubListWidget`: the state builder is now an internal top-level
  `listStates(repository, iconPx, iconBitmap: suspend (String, Int) -> Bitmap?)`. Production passes
  `icons::iconBitmapOnMain`, and only `latestUnread` runs on IO. Because the builder only accepts the
  suspend function, it cannot call the synchronous variant. I used a function parameter rather than a
  fake `AppIconCache` subclass because `AppIconCache` is final. `AppCountWidget` gets labels on IO and
  icons through `iconBitmapOnMain`.
- **Also fixed:** `HubRow.rememberAppIcon` had the same bug. It called `toBitmap` inside
  `withContext(Dispatchers.IO)`, so it was not on the main thread as the message assumed. It now looks
  up the drawable on IO and draws it through `iconBitmapOnMain`.
- Tests:
  - `AppIconCacheTest.iconBitmap_isCachedPerSize`: per-size bitmaps, the same instance after the
    package is deleted, through both variants.
  - `HubListWidgetTest.listStates_drawIconsThroughTheMainThreadVariant`: the builder asks the
    injected suspend function once per row, at the widget icon size.
- I also removed two stale "Reply" mentions from the list widget's comments, left over from the
  earlier removal.

## Item 3: unread badge on the launcher icon

- New `badge/UnreadBadge` (`@Singleton`).
  - It combines `prefs.unreadBadge` with `repository.unreadCount()`, applies
    `distinctUntilChanged`, then posts or cancels.
  - It uses an application-scoped `SupervisorJob + Dispatchers.Default` scope, and `start()` is
    idempotent.
  - The notification and channel match the brief: channel `"unread"` / "Unread count",
    `IMPORTANCE_LOW`, `setShowBadge(true)`, id 1, `ic_stat_hub`, title "Hub" (`app_name`), plural text
    "N unread notifications", `setNumber`, `setOnlyAlertOnce`, `setSilent`, not ongoing, content
    intent `HubIntents.open(tab = "UNREAD")` with `FLAG_IMMUTABLE`.
  - It cancels at 0 or when the setting is off. On API 33+ without `POST_NOTIFICATIONS`, or with
    notifications disabled, it posts nothing, and a `SecurityException` race is swallowed.
- Started from `HubApp.onCreate` (through `dagger.Lazy`) and from `HubListenerService.onCreate`.
  **My call:** `HubApp` now returns early when WorkManager is not initialised, the same signal it
  already used to tell JVM tests from a device. As a result, neither the purge schedule nor the badge
  starts under Robolectric.
- Setting "Unread badge on the app icon" / "Shown through a silent Hub notification", default on,
  `HubPrefs.unreadBadge`, icon `MarkEmailUnread`.
- `POST_NOTIFICATIONS` is back in the manifest. The onboarding request is restored with the old
  pattern: API 33+, asked once after listener access, and onboarding finishes whether or not it is
  granted. `ic_stat_hub` was still present, so nothing needed restoring.
- Tests (`UnreadBadgeTest`, Robolectric `ShadowNotificationManager`):
  - `postsWhenUnread` checks number, text, channel importance, badge flag and not ongoing.
  - `cancelsAtZero`.
  - `offWhenSettingDisabled` checks off, then on, then off again.
  - Plus `withoutPermission_postsNothing`.

## Docs (`ea599ca`, plus spec edits in `a64afa0` and `5b668be`)

- Spec 4.8 lists both settings. The new **4.12 Launcher badge** sits after 4.11. 4.6 no longer says
  Hub posts nothing of its own, and 4.10 names the badge's small icon.
- The README gives one line each for the notification permission (badge), the shade option and the
  `badge/` package.
- 4.2 describes the multi-select filter and the bare-icon row anatomy.
- `docs/mockups/screens.html` has a visible "Superseded on 2026-11-01: ..." paragraph at the top of
  the body.

## Item 4: multi-select app filter

- The filter is now a set of package names:
  - `HubFilter.packageNames: Set<String>`, where the empty set means every app.
  - `HubRepository.unreadCount`, `markAllRead` and `unreadNotifKeys` take a `Set<String>`.
  - The DAO paging queries, `unreadCount`, `unreadNotifKeys` and `markAllRead` use
    `(:all OR packageName IN (:pkgs))`, with the set passed as a list.
  - **My call:** Mark all read and its notification cancels follow the ticked set, keeping the old
    "for the filtered app" behaviour.
- `HubViewModel.packageFilter: StateFlow<Set<String>>` with `toggleApp`, `clearFilter`, and
  `selectOnlyApp`. I named the last one myself; it is used for `EXTRA_PACKAGE` from the widget app tap.
- `AppFilterSheet`:
  - "Every app" clears the filter and closes the sheet.
  - App rows toggle a trailing `Checkbox`, show their unread badge beside it, and keep the sheet open.
  - A "Done" text button closes it.
- The chip label comes from `filterLabel()`: "Only WhatsApp", "WhatsApp, Teams" (labels sorted), or
  "N apps". New strings: `filter_active_two`, plural `filter_active_many`, `action_done`.
- Search keeps its single-select chips.
- Tests:
  - `HubViewModelTest.filterApps_toggleAndClear` covers filters requested, unread badge count,
    `selectOnlyApp` and clear.
  - `RoomHubRepositoryTest.pagedItems_filtersByPackageSet` covers all three tabs, the empty set,
    `unreadCount` and `markAllRead` on a set.
  - `HubScreenTest.filterLabel_oneTwoMany`.
  - Existing tests and all three fakes were updated.

## Item 5: app icons without a background tile

These places now draw the bitmap as it is, at the same size, with no background, clip or corner
radius:

- `HubRow.AppIcon`, used by list rows and the filter sheet. Its content description moved onto the
  `Image`.
- `ui/search/AppIconImage`, used by search and Settings.
- `HubListWidget` and `AppCountWidget`.

The existing fallback tiles stay and are only used when the icon cannot be loaded: the letter tile in
the app, and the tray-glyph tile in the widgets. `IconShape` is still used by the select-mode tick and
the fallback tile, so nothing became unused. The greyscale filter on read rows is unchanged.

## Self-review

- An unused-string and unused-import script over `app/src` shows nothing new. The four strings that
  were already unused (`search_clear_recent`, `settings_app_today_count`, `settings_dynamic_colour`,
  `onboarding_continue`) are untouched.
- The British English pass is done, and there are no emoji.

## Concerns

1. Nothing here is checked on a device:
   - the badge on a real launcher (some show only a dot for `IMPORTANCE_LOW`);
   - the shade clearing;
   - the multi-select sheet layout (the list takes `weight(1f, fill = false)` so Done stays visible);
   - that the widgets no longer crash.
2. `AppIconImage` (search and Settings) still calls `toBitmap` without the cache, inside `remember`
   during composition. That is on the main thread, so it is safe, but it does not share the new cache.
3. With "Keep the status bar clear" on, a notification Hub skips (an excluded app, a silent one while
   hiding silent ones, or an ongoing one) is deliberately left in the shade, because only captured
   items are cancelled.

## Fix report (after review, 2026-11-01)

| SHA | Subject | Suite after |
|---|---|---|
| `741cbbe` | fix(capture): keep the content intent when Hub clears the shade | 95 |
| `f09c6e1` | refactor(icon): drop the synchronous iconBitmap; add a cache peek | 95 |
| `acb7a41` | fix(list): show cached app icons on the first frame | 95 |
| `be2395d` | fix(badge): post the badge once the permission is granted | 96 |

Each commit was gated with `./gradlew :app:assembleDebug :app:testDebugUnitTest`. I checked
`f09c6e1` on its own, with the next change stashed. **Final: BUILD SUCCESSFUL, 96 tests in 20
classes, 0 failures, 0 errors, 0 skipped.**

1. **(Important) Clear-shade kept the deep link.**
   - `HubListenerService` keeps `clearedForShade`, a `ConcurrentHashMap.newKeySet()`. A key is added
     just before the shade-option cancel.
   - In `onNotificationRemoved`, a `REASON_LISTENER_CANCEL` removal of a key in that set takes the
     key out and skips `registry.unregister`. The skip is one-shot. Every other removal still
     unregisters, including Hub's cancels after a read or open.
   - **My call on the leak: an LRU cap.** `LiveActionsRegistry` is now an access-ordered
     `LinkedHashMap`, synchronised and capped at `MAX_ENTRIES = 500` with the least recently used
     entry evicted. A re-post still overwrites its entry. I chose this over a `forget(key)` from the
     delete and purge paths for two reasons. It bounds every way an entry can outlive its
     notification, not only the shade option. And it needs no new repository-to-registry dependency.
   - Tests:
     - `posted_withClearShade_cancelsAfterCapture` now delivers `REASON_LISTENER_CANCEL` after the
       cancel. It asserts `openContent` is still true and nothing was marked read. I watched it fail
       without the fix.
     - The new `removal_userCancel_stillUnregisters` covers a user swipe, a listener cancel Hub did
       not make for the shade option, and a second listener cancel of a shade-cleared key.
     - The new `LiveActionsRegistryTest.register_beyondTheCap_evictsTheLeastRecentlyUsed`.
2. **(Minor) Flicker.**
   - `rememberAppIcon` seeds `produceState` from `AppIconCache.cachedBitmap(pkg, sizePx)`, held in a
     `remember` per package and size. When it finds a bitmap it does not load again.
   - Only icons that have never been drawn pass through the letter tile.
   - There is no automated test. Showing it would need a Compose frame-timing test, which is not
     worth the cost here.
3. **(Minor) Main-thread contract.**
   - Nothing in `app/src/main` called the synchronous `iconBitmap`, so I removed it rather than adding
     a `check`. Drawing now only happens in `iconBitmapOnMain`.
   - The new `cachedBitmap` only peeks at the cache and never draws.
   - `AppIconCacheTest.iconBitmap_isCachedPerSize` now uses `cachedBitmap` and `iconBitmapOnMain`.
4. **(Minor) Badge after a late permission grant.**
   - `UnreadBadge.refresh()` bumps a counter that is combined into the collected flow. That re-shows
     the current count even when it has not changed.
   - `MainActivity.onResume` calls it through `dagger.Lazy`.
   - Test: `UnreadBadgeTest.refresh_postsOncePermissionIsGranted`.

No new concerns. The deferred items are left as the review said.

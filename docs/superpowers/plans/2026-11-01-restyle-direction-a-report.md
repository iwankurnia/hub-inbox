# Restyle direction A: implementation report

Branch `main`, from 0c5861d. Four commits, none pushed. No emulator or adb used.

| SHA | Subject |
| --- | --- |
| e0061fe | style(theme): neutral palette for direction A |
| 1fd1fce | style(list): compact bar, text tabs, tightened rows |
| 19a908a | style(ui): search, settings, onboarding, sheet and widgets in the neutral palette |
| eb30e70 | docs: spec for direction A |

Gate after every commit: `./gradlew :app:assembleDebug :app:testDebugUnitTest` green.
Final: **98 tests, 0 failures, 0 errors, 0 skipped** (96 before; two tests added). No compiler warnings printed.

## What changed

### Theme (`ui/theme`, e0061fe)
- `HubLightColors` / `HubDarkColors` rebuilt explicitly with the brief's values. Primary = onSurface, primaryContainer = surfaceContainerHigh, `secondary` = the "secondary text" grey (#8A8E97 / #8E9199). Secondary, tertiary and their containers are neutral greys too, so no Material default (FilterChip, Checkbox, RadioButton, menus) shows a blue tint. Error roles are the Material defaults.
- `HubColors` is now light/dark aware through a `LocalHubColors` CompositionLocal provided by `HubTheme`: readReveal #E3E5EA / #2A2F36, onReadReveal = onSurface. There were no amber tokens to drop. The `HubSeed` constant is gone.
- `HubTheme(dynamicColor = false)` by default; dynamic colour is still honoured when on. `HubPrefs.dynamicColor`, `SettingsViewModel` and `MainActivity` now default to false.
- `MainActivity.enableEdgeToEdge` uses `SystemBarStyle.auto(TRANSPARENT, TRANSPARENT)` for both bars, so there is no scrim and the icons follow light/dark.
- The window background colours are #FBFBFE / #0F1114.
- Typography: `titleLarge` is 20sp SemiBold with −0.2sp letter spacing. `bodyLarge` is not changed globally, because ListItem, TextField and Settings rows use it; the row title tightens it locally to 14sp instead.

### Hub screen (`ui/hub`, 1fd1fce)
- The top bar is a `TopAppBar` with `expandedHeight` 56dp, a `surface` container, the "Hub" title in `titleLarge`, and filter (with the dot badge), search and `MoreVert` actions. The overflow has "Mark all read", shown when the tab is not Deleted and the unread count is above zero, and "Settings". The standalone settings icon and the display title row are gone.
- Tabs are a custom left-aligned `Row` with `selectableGroup` and `Role.Tab`: 16dp edge padding, 22dp gaps, 13.5sp SemiBold labels in onSurfaceVariant (onSurface when active), and a 2dp onSurface indicator under the label and count. There are no icons. The unread count is a plain 12sp Medium onSurfaceVariant number with a 5dp gap, and a 1dp outlineVariant hairline runs under the row.
- The active-filter chip is tonal: surfaceContainerHigh, onSurface label and close icon, no border.
- Date band: surfaceContainer, 7dp vertical and 16dp horizontal padding. The label is upper-cased, 11sp SemiBold with 0.8sp spacing; the right-hand text is 11sp Medium with no capitals. `DateBands` now gives "Sun 1 Nov" for Today and Yesterday.
- Row: a 3dp onSurface bar, a 32dp icon (the tick tile is also 32dp with an 8dp radius), and padding of 10dp vertical, 12dp start (after the bar) and 16dp end.
  - Title: `bodyLarge` at 14sp, Bold when unread, Normal when read. Title and time are baseline-aligned.
  - Time: 11.5sp, onSurface SemiBold when unread, `secondary` Normal when read.
  - Preview: 12.5sp, onSurface at 85 % when unread, onSurfaceVariant when read.
  - Divider: 1dp outlineVariant, drawn only when the next visible row is in the same band. A new `nextVisible` helper mirrors `previousVisible` and skips rows that are hidden pending a purge.
- Select mode: the `SelectBar` is 56dp on surfaceContainerHigh with 16sp SemiBold "N selected" and onSurface icons. Selected rows take surfaceContainerHigh, and the tick is onSurface on a surfaceContainerHigh tile.
- Deleted tab: the banner stays errorContainer. Its "Empty now" button is now onErrorContainer, where primary would have been black on red. The Restore chip is surfaceContainerHigh with onSurface.
- Swipes: the read reveal uses the new `HubColors` and delete keeps errorContainer. The swipe's idle background and the Scaffold container are `surface`, no longer surfaceContainerLowest.
- Snackbar: the default Material snackbar. Its action uses `inversePrimary`, which is #A8C7FA in light and #0B57D0 in dark.

### Other screens and widgets (19a908a)
- Search:
  - The screen background is `surface`, the query pill is surfaceContainer with tighter padding (12/4dp), and a hairline sits under the app chips.
  - `SearchResultRow` now copies the list row: 3dp bar, 32dp icon, the same type sizes, weights and colours, and the same padding. The fixed 72dp height is gone and the last result has no hairline.
  - The highlight is surfaceContainerHigh, and recent-search rows sit on `surface`.
- Settings:
  - The `TopAppBar` is 56dp on `surface` with a `titleLarge` title. Section labels are upper-cased 11sp SemiBold with 0.8sp spacing in onSurfaceVariant.
  - Groups have a 16dp radius on surfaceContainer, and hairlines are 1dp.
  - Every `Switch` uses `checkedTrackColor = onSurface`, `checkedThumbColor = surface`, `checkedIconColor = onSurface`.
  - The Theme row already reads "Follows system" when dynamic colour is off, and the switch is now off by default.
- Onboarding: the tray tile is surfaceContainerHigh with an onSurface glyph. The button is onSurface with `surface` text, and the access card radius is 16dp.
- Filter sheet: the container is `surface`. Selected rows are surfaceContainerHigh and the rest `surface`. The "Every app" tick is onSurface, checkboxes are onSurface with a `surface` checkmark, and the unread `Badge` pills are now plain 12sp onSurfaceVariant numbers.
- Widgets:
  - `HubWidgetTheme` now always uses the neutral `ColorProviders`, with no `DynamicThemeColorProviders`.
  - The header glyph and the 3dp unread bar are onSurface. The count pill is `primaryContainer`, which equals surfaceContainerHigh in our scheme, with onSurface text. The Read chip is onSurface on the same container.
  - Per-app badges are onSurface with `surface` text, and the total glyph is onSurface.
  - The widget-picker preview layouts swap `colorAccent` for `textColorPrimary`.

### Docs (eb30e70)
- Spec 4.2: compact bar with Settings in the overflow, text tabs with the note "Tab icons removed with direction A (Iwan, 2026-11-01)", band and date formats, 32dp icon, 3dp unread bar, and dynamic colour off by default.
- Spec 4.11 is rewritten with a palette table, chrome, list, Settings and widget rules. The Material Symbols list is kept, with `filter_list` added because it is in use.
- Spec 4.8 (Theme row) and 4.9 (widgets) are brought in line with these changes.

## Values chosen where the brief was silent
- **Bar icon colour:** onSurface at 80 %, which approximates the mockup's #44474E on light and works on dark.
- **Weekday bands (2–6 days ago):** the right-hand text is "29 Oct" (day and short month), not "Thu 29 Oct", to avoid repeating the weekday in the label. Older bands keep the full date on the left and the year on the right. This is the quieter option.
- **Hairline under the tabs:** I removed the former divider under the filter chip, so the chip sits between the tab hairline and the list.
- **Inverse surfaces:** inverseSurface is #2F3033 (light) / #E6E8EC (dark) and inverseOnSurface is #F1F1F4 / #2F3033. The surfaceContainer tiers not in the brief are interpolated: Lowest #FFFFFF / #0A0C0E, Low #F7F7FB / #15181C, Highest = outlineVariant.
- **Tab indicator width:** it spans the label plus the count, following the mockup's `::after` on `.tab`. The brief says "width of the label".
- **Status colour:** "Granted" in Settings stays green (#0B6B2E) because it is a status colour, not an accent.
- **Corner radii:** the selected tick tile is a rounded square with an 8dp radius, as before (scaled from 9dp at 34dp), not a circle. The onboarding card radius is 16dp to match the Settings groups.

## Tests changed
- `HubPrefsTest`: the dynamic-colour default is now false, and the setter test sets it to true.
- `DateBandsTest`: the right-hand texts are now "Sun 1 Nov", "Sat 31 Oct", "29 Oct" and "26 Oct".
- `HubScreenTest`: band label lookups use `ignoreCase = true` because labels are now drawn in capitals. I added two tests:
  - `overflow_opensSettings_andOffersMarkAllReadOnlyWithUnread` covers the moved Settings entry and the conditional menu item.
  - `unreadTab_showsPlainCount` covers the plain number that replaced the badge.
- No behaviour test was removed or weakened.

## Self-review findings
- I checked the diff against the brief section by section; each bullet is implemented as described above.
- "Mark all read" now also appears on the All tab when there are unread items. Before, it was in the overflow only on the Unread tab. The brief's wording ("only when the current tab has unread") asks for this, and `markAllRead()` is already filter-aware.
- The selected tick tile (surfaceContainerHigh) sits on a selected row of the same colour, so only the tick shows. This is literally what the brief asks for; check it on the device.
- Text tabs are about 40dp tall (mockup padding), under the 48dp touch-target guideline, but the whole label and count area is the target.

## Concerns
- Nothing has been checked on a device or emulator; the controller is to do that.
- `HubRow.kt` has a small private `@Composable fun TitleStyle()` returning a `TextStyle`. Compose lint would prefer a lower-case name. It is cosmetic; rename it if the review wants.

## Files changed
- app/src/main/java/com/iwankurnia/hub/MainActivity.kt
- app/src/main/java/com/iwankurnia/hub/data/HubPrefs.kt
- app/src/main/java/com/iwankurnia/hub/ui/theme/{Color,HubTheme,Type}.kt
- app/src/main/java/com/iwankurnia/hub/ui/hub/{HubScreen,HubRow,DateBands,SelectBar,SwipeRow,AppFilterSheet}.kt
- app/src/main/java/com/iwankurnia/hub/ui/search/{SearchScreen,SearchResultRow}.kt
- app/src/main/java/com/iwankurnia/hub/ui/settings/{SettingsScreen,SettingsViewModel}.kt
- app/src/main/java/com/iwankurnia/hub/ui/onboarding/OnboardingScreen.kt
- app/src/main/java/com/iwankurnia/hub/widget/{WidgetTheme,HubListWidget,AppCountWidget}.kt
- app/src/main/res/values/colors.xml, app/src/main/res/values-night/colors.xml
- app/src/main/res/layout/widget_hub_list_preview.xml, widget_hub_list_preview_row.xml
- app/src/test/java/com/iwankurnia/hub/data/HubPrefsTest.kt
- app/src/test/java/com/iwankurnia/hub/ui/hub/{DateBandsTest,HubScreenTest}.kt
- docs/superpowers/specs/2026-11-01-hub-inbox-design.md

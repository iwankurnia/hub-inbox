# Restyle: direction A, "tightened Material"

Iwan, 2026-11-01: "redesign so it looks more professional, BB10 feel but in the Android design". Of three mockups he chose A. Reference: `docs/mockups/redesign-direction-a.html` (phone A; B and C are the rejected options). Behaviour does not change; only the look.

## Palette and theme (`ui/theme`)

- Neutral scheme. Light: background `#FBFBFE`, surface `#FBFBFE`, surfaceContainer `#F2F3F8`, surfaceContainerHigh `#E8E9EF`, outlineVariant `#E2E3EA`, onSurface `#1A1C1E`, onSurfaceVariant `#6B6F78`, secondary text `#8A8E97`. Dark: background `#0F1114`, surface `#0F1114`, surfaceContainer `#1B1E23`, surfaceContainerHigh `#23272D`, outlineVariant `#2A2F36`, onSurface `#E6E8EC`, onSurfaceVariant `#A9ADB5`, secondary `#8E9199`. Primary = onSurface (black / white); primaryContainer = surfaceContainerHigh. Error/errorContainer keep Material defaults (delete reveal, Empty-now banner).
- `HubTheme`: build these two `ColorScheme`s explicitly; dynamic colour is still honoured when the setting is on, but `HubPrefs.dynamicColor` now defaults to **false**. Follows system dark mode as before.
- `HubColors`: `readReveal` → light `#E3E5EA` / dark `#2A2F36` with `onReadReveal` = onSurface; drop the amber tokens if still present.
- Status bar and navigation bar: transparent over `surface` (edge-to-edge), icons dark on light / light on dark.

## Hub screen (`ui/hub`)

- Top bar: `TopAppBar` 56dp, container `surface`, title "Hub" `titleLarge` 20sp weight 600 letter-spacing −0.2sp, actions: filter (`FilterList`, with the small dot badge when a filter is active), search, overflow (`MoreVert`). Overflow menu items: "Mark all read" (only when the current tab has unread), "Settings". Remove the standalone settings icon and the old display title row.
- Tabs: `TabRow`/`PrimaryTabRow` replaced by a left-aligned, text-only row (`ScrollableTabRow` with `edgePadding = 16.dp` or a custom `Row`): labels All, Unread, Deleted at 13.5sp weight 600; inactive `onSurfaceVariant`, active `onSurface`; indicator 2dp `onSurface`, width of the label; no icons; the unread count is a plain `onSurfaceVariant` number at 12sp after the label ("Unread 6"), no badge pill. Hairline `outlineVariant` under the row.
- Active-filter chip stays under the tabs but tonal neutral (`surfaceContainerHigh`, `onSurface` text, close icon).
- Date band: `surfaceContainer` strip, 7dp vertical padding, left label uppercase 11sp weight 600 letter-spacing 0.8sp `onSurfaceVariant`; right text ("Sun 1 Nov" short weekday + day + short month) 11sp weight 500, not uppercase.
- Row: 3dp left bar `onSurface` when unread (transparent otherwise); 32dp bare icon (greyscale when read, as now); title 14sp (`bodyLarge` tightened), weight 700 when unread else 400; time 11.5sp, `onSurface` weight 600 when unread else `secondary` weight 400; preview 12.5sp, `onSurface` at 85 % when unread else `onSurfaceVariant`; row padding 10dp vertical, 12dp start, 16dp end; hairline divider `outlineVariant` at 1dp between rows (no divider after the last row before a band).
- Select mode: top bar becomes `surfaceContainerHigh` with "N selected" 16sp weight 600 and the same icons; selected rows tint `surfaceContainerHigh`; the tick replaces the icon in `onSurface` on `surfaceContainerHigh`.
- Deleted tab: banner stays `errorContainer`; Restore chip tonal neutral.
- Swipe reveals: read/unread reveal uses `readReveal`/`onReadReveal`; delete keeps `errorContainer`.
- Snackbar: default Material dark snackbar with `inversePrimary` action = keep readable (set action colour to `inverseOnSurface`-compatible light blue is fine).

## Other screens

- Search: same compact bar (back, query field, clear), results use the same row style; highlight = `surfaceContainerHigh` background.
- Settings: `TopAppBar` 56dp "Settings"; section labels uppercase 11sp letter-spaced `onSurfaceVariant`; grouped cards `surfaceContainer` with 16dp radius (down from 24); switches use `onSurface` thumb/track colours (`SwitchDefaults.colors(checkedTrackColor = onSurface, checkedThumbColor = surface)`); "Theme" row subtitle reads "Follows system" and the switch is "Dynamic colour (off)" by default.
- Onboarding: same neutral palette; the tray tile becomes `surfaceContainerHigh` with an `onSurface` glyph; primary button `onSurface` background with `surface` text.
- Filter sheet: `surface` sheet, rows as now, tick in `onSurface`, badge counts as plain numbers.
- Widgets: header text `onSurface`, unread bar `onSurface` 3dp, count pill `surfaceContainerHigh`, no blue anywhere; the per-app bar's badges `onSurface` with `surface` text.
- Launcher icon unchanged.

## Docs and gate

- Spec section 4.11 rewritten for this palette and chrome (keep the Material Symbols list); note "tab icons removed with direction A (Iwan, 2026-11-01)" in 4.2.
- Update the Compose UI tests that assert colours or the tab badge if any; keep every behaviour test green. `./gradlew :app:assembleDebug :app:testDebugUnitTest` green, exact count reported. No emulator (the controller checks on device).
- Commits: `style(theme): neutral palette for direction A`, `style(list): compact bar, text tabs, tightened rows`, `style(ui): search, settings, onboarding, sheet and widgets in the neutral palette`, `docs: spec for direction A`.

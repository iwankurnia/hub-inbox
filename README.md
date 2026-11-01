# Hub

A BlackBerry-Hub-style unified inbox for Android. Hub listens to every
notification your phone receives, keeps it in a local database, and shows it
as one chronological stream in Android 16 (Material 3) styling: BB10-style
flat rows with date bands, tap to open the source app, swipe to read or
delete, long-press to select, full-text search, and two home-screen widgets.

Nothing leaves the phone: there is no network permission.

- Design: `docs/superpowers/specs/2026-11-01-hub-inbox-design.md`
- Mockups: open `docs/mockups/screens.html` in a browser
- Implementation plan: `docs/superpowers/plans/2026-11-01-hub-inbox.md`

## Requirements

- Android 8.0 (API 26) or newer; dynamic colour needs Android 12, themed
  icons Android 13.
- To build: Android Studio (its bundled JDK is used through
  `~/.gradle/gradle.properties`), the Android SDK with platform 37 and
  build-tools 36, Gradle wrapper 9.5 (bundled).

## Build and install

Every APK build bumps `versionCode` in `version.properties` and names the
file after the version (`versionName` = `marketingVersion.versionCode`):

```bash
./tools/build-apk.sh
```

The APK lands in `dist/hub-<version>.apk` (the folder is git-ignored; the
bumped `version.properties` is committed with the build). A plain
`./gradlew :app:assembleDebug` still works but does not bump the version.

Connect the phone with USB debugging on, then:

```bash
./gradlew :app:installDebug
```

```bash
adb shell am start -n com.iwankurnia.hub/.MainActivity
```

On first run Hub asks for notification access (required) and, on
Android 13+, to post notifications (only for the unread badge on the app
icon, a silent Hub notification; it can be turned off in Settings). It is a
sideloaded debug build; there is no Play Store listing.

Settings also offers "Keep the status bar clear", which removes each
notification from the shade ten seconds after it arrives, once its sound
and heads-up banner have played. "Dynamic colour" tints only the top bar,
the tabs and the date bands from the wallpaper; the list stays neutral.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

JVM tests run under Robolectric (API 35) against an in-memory Room
database; there are no emulator tests.

## Layout

```
app/src/main/java/com/iwankurnia/hub/
  capture/   NotificationListenerService, mapper, filter, content-intent registry, purge worker
  badge/     the unread count on the launcher icon, through one silent notification
  data/      Room entities, DAO, repository, DataStore preferences
  ui/        opening an item in its app (shared by the list and search)
  ui/hub     the Hub screen: tabs, chips, date bands, rows, swipes, select mode
  ui/search  full-text search
  ui/settings, ui/onboarding
  widget/    Glance widgets: notification list and per-app counts
```

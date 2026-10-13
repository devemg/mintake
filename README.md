# Mintake

Medicine reminders for Android phones.

- **Phone app (`mobile`)**: add, edit, pause and delete reminders (name, strength, dose, time, repeat: daily, weekdays or every N hours), see today's doses and their status, get notifications to mark each dose, and export reminders and history as JSON (Settings).
- **`shared`**: data model, schedule logic and local storage (DataStore).

> The Wear OS app (`wear`) and phone↔watch sync were removed. They are still in the git history if ever needed.

## Open in Android Studio

1. **File › Open** and pick the `Mintake` folder (the one with `settings.gradle.kts`).
2. Let Gradle sync. Android Studio may offer to upgrade the Android Gradle Plugin or libraries. It's fine to accept.
3. Run the **mobile** configuration.

Requirements: a recent Android Studio, Android SDK 35, JDK 17+ (the one bundled with Android Studio works).

## Run on your phone

Enable Developer options + USB debugging on the phone, connect it and run **mobile**. An emulator works too.

## Reminders

`MobileReminderScheduler` keeps one exact alarm set for the next due dose (or the end of a snooze). When it fires, a high-priority notification shows the medicine with **Done**, **Snooze** and **Not taken**. Alarms are re-armed after reboot, app update and time changes.

> **Google Play note:** exact timing uses the `USE_EXACT_ALARM` permission. Google Play only allows it for alarm-clock and calendar apps. Before publishing, switch to `SCHEDULE_EXACT_ALARM` (ask the user to allow it) or accept slightly inexact alarms.

## Project layout

```
shared/   Models.kt, Schedule.kt, MintakeRepository.kt
mobile/   ui/MobileApp.kt (bottom bar + add button shell), ui/HomeScreen.kt, ui/RemindersScreen.kt, ui/SettingsScreen.kt, ui/AddReminderScreen.kt, ui/Theme.kt
          reminders/MobileReminderScheduler.kt, reminders/MobileReminderNotifications.kt, reminders/MobileReceivers.kt
```

The launcher icon (capsule in a progress ring) is a vector adaptive icon in `mobile/src/main/res/drawable/ic_launcher_foreground.xml`, with a monochrome layer for Android 13+ themed icons.

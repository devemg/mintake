# Mintake

Medicine reminders for Galaxy Watch (Wear OS), with a phone companion app.

- **Phone app (`mobile`)**: add and delete reminders (name, strength, dose, time, repeat: daily, weekdays or every N hours), see today's doses and their status, sync, and open Mintake on the watch.
- **Watch app (`wear`)**: today's doses with a progress ring, a "Time for your medicine" screen with **Done / Not taken / Snooze 30 min**, alarms + notifications with the same three actions, sync, and open Mintake on the phone.
- **`shared`**: data model, schedule logic, local storage (DataStore) and phone↔watch sync (Wear OS Data Layer).

## Open in Android Studio

1. Unzip, then **File › Open** and pick the `Mintake` folder (the one with `settings.gradle.kts`).
2. Let Gradle sync. Android Studio may offer to upgrade the Android Gradle Plugin or libraries. It's fine to accept.
3. Pick the run configuration: **mobile** for the phone, **wear** for the watch.

Requirements: a recent Android Studio, Android SDK 35, JDK 17+ (the one bundled with Android Studio works).

## Run on your phone and Galaxy Watch

1. Phone: enable Developer options + USB debugging, connect it, run **mobile**.
2. Watch: on the watch go to Settings › About watch › Software information and tap *Software version* 5 times to enable Developer options. Then turn on **ADB debugging** and **Wireless debugging**, and pair it from Android Studio (**Device Manager › Pair devices using Wi-Fi**). Run **wear** on it.
3. The watch must be paired with the phone (Galaxy Wearable app).

Both apps share the applicationId `com.mintake.reminders` and must be signed with the **same key**. Debug builds from the same computer already are. This is what lets them sync.

To try it with emulators instead: create a Wear OS emulator and pair it with a phone emulator that has Google Play (Device Manager › *Pair Wearable*).

## How sync works

Each device keeps the full state (`AppState`) locally and sends it to the other through a Data Layer item at `/mintake/state`. When one arrives, the two are merged. The newest medicine list wins (only the phone edits it), and dose statuses are merged one by one, newest wins. "Open on phone/watch" uses `RemoteActivityHelper` with the deep link `mintake://open`.

## Reminders on the watch

`ReminderScheduler` keeps one exact alarm set for the next due dose (or the end of a snooze). When it fires, a high-priority notification shows the medicine with **Done**, **Snooze 30 min** and **Not taken**. Alarms are re-armed after reboot, app update and time changes.

> **Google Play note:** exact timing uses the `USE_EXACT_ALARM` permission. Google Play only allows it for alarm-clock and calendar apps. Before publishing, switch to `SCHEDULE_EXACT_ALARM` (ask the user to allow it) or accept slightly inexact alarms (the code already falls back to them).

## Project layout

```
shared/   Models.kt, Schedule.kt, MintakeRepository.kt, DataLayerListenerService.kt, RemoteOpener.kt
mobile/   ui/HomeScreen.kt, ui/AddReminderScreen.kt, ui/Theme.kt
wear/     ui/WearApp.kt, ReminderScheduler.kt, ReminderNotifications.kt, Receivers.kt
```

The launcher icon (capsule in a progress ring) is a vector adaptive icon in each module's `res/drawable/ic_launcher_foreground.xml`, with a monochrome layer for Android 13+ themed icons.

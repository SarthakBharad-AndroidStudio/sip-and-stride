# Sip & Stride

A hydration and walking tracker for Android, where the two halves of the app depend on each other: **the more you walk, the more water the app expects you to drink.**

Built as the final project for *Mobile Systems (MobSys 2026)* at Hochschule Schmalkalden — University of Applied Sciences.

![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-14%E2%80%9316%20(API%2034%E2%80%9336)-3DDC84?logo=android&logoColor=white)
![Views](https://img.shields.io/badge/UI-XML%20Views-orange)
![License](https://img.shields.io/badge/License-MIT-blue)

---

## What it does

The daily water goal is not a fixed number. It is recalculated from body weight and the steps actually recorded that day:

```
goal_ml = weight_kg × 33 + (steps ÷ 1000) × 100      → rounded to the nearest 250 ml
```

A 70 kg person who hasn't walked gets 2250 ml. After 6000 steps the goal rises to 3000 ml.

- **Record a walk** — the phone counts steps with a motion sensor and stamps the start and end of the walk with a GPS position, reverse-geocoded to a street address. Steps become a distance via your body height (a stride is about 41.5 % of height).
- **Log water** — one tap per glass, with an undo button. The glass size is configurable.
- **Track the day** — a hand-drawn ring shows how much of today's goal is reached, beside today's step total.
- **Review walks** — every walk is listed; open one to see it on a map, share it as text, or delete it.

## Screenshots

| Dashboard | Recording a walk | Walk details |
|:--:|:--:|:--:|
| ![Dashboard](docs/screenshots/dashboard.png) | ![Walk](docs/screenshots/walk.png) | ![Detail](docs/screenshots/detail.png) |

| Permission rationale | Settings | Dark mode |
|:--:|:--:|:--:|
| ![Permissions](docs/screenshots/permissions.png) | ![Settings](docs/screenshots/settings.png) | ![Dark](docs/screenshots/dark.png) |

> Put the PNGs in `docs/screenshots/` with these names and the table fills itself in.

## Built with

Kotlin, XML views, and the Android framework only — no Room, no Retrofit, no Glide, no Play Services location. The full dependency list is AndroidX AppCompat, Material Components, `core-ktx`, `activity-ktx` and ConstraintLayout, which is what the Android Studio *Empty Views Activity* template ships with.

| Area | Implementation |
|---|---|
| Motion sensor | `Sensor.TYPE_STEP_COUNTER` via `SensorManager`, with an accelerometer step-detection fallback for devices (and emulators) without one |
| Position sensor | `LocationManager` (GPS, network provider as backup) plus `Geocoder` for reverse geocoding |
| Custom view | `WaterRingView` — a progress ring drawn with `Canvas`, `Paint`, `drawArc()` and `drawText()` |
| List | `RecyclerView` with a custom adapter, `ViewHolder` and a `fun interface` click listener |
| Storage | `SharedPreferences` — settings as key/value pairs, the water total under a per-day key, walks serialised into one string |
| Navigation | Explicit intents with `ActivityResultLauncher` for two-way data transfer |
| Implicit intents | `geo:` (open a map), `ACTION_SEND` (share), `ACTION_APPLICATION_DETAILS_SETTINGS` (blocked permissions) |

## Project structure

```
app/src/main/
├── java/com/example/sipandstride/
│   ├── WalkSession.kt        data class for one walk, Serializable
│   ├── SessionStore.kt       all persistence + the goal and stride formulas
│   ├── WaterRingView.kt      the custom-drawn progress ring
│   ├── SessionAdapter.kt     RecyclerView adapter
│   ├── MainActivity.kt       dashboard
│   ├── WalkActivity.kt       sensors, permissions, recording
│   ├── DetailActivity.kt     one walk: map / share / delete
│   └── SettingsActivity.kt   body data and preferences
└── res/
    ├── layout/               5 layouts
    ├── menu/                 toolbar menu
    ├── values/               strings, colors, themes, attrs
    ├── values-night/         dark theme
    └── drawable/             vector icons
```

One rule runs through it: **activities own the screen, `SessionStore` owns the data.** No activity touches `SharedPreferences` directly — which is why `DetailActivity` returns the id of a deleted walk to the dashboard instead of deleting it itself.

## Build and run

```bash
git clone https://github.com/SarthakBharad-AndroidStudio/sip-and-stride.git
```

Open the folder in **Android Studio 2025/2026** and press Run. Gradle downloads what it needs on first sync.

Requirements: JDK 17+ (bundled with Android Studio), Android SDK 34 and 36, and a device or emulator on Android 14–16.

### Testing on the emulator

- **Location:** Extended controls (`…`) → *Location* → enter coordinates → **Set Location**. Use a system image with **Google APIs** — `Geocoder` needs Play services and a network connection, or every address comes back empty.
- **Steps:** the emulator has no step-counter hardware, so the accelerometer fallback runs instead. Move the virtual device under *Virtual sensors* to generate steps, or use the **Simulate 10 steps** button on the walk screen.

## Known limitations

- Sensor listeners are released in `onStop()`, so steps are not counted while the app is in the background. A production app would use a foreground `Service`.
- The accelerometer fallback is approximate — a shaken phone counts as walking.
- Reverse geocoding needs a network connection; without one, walks are saved with no address.
- `SharedPreferences` is used instead of a database. Fine for a few dozen walks, not for years of history.

## License

MIT — see [LICENSE](LICENSE).

This is coursework. You're welcome to read it, learn from it and reuse the code, but do not submit it as your own for a graded assignment.

## Author

**Sarthak D. Bharad** — M.Sc. Applied Computer Science, Hochschule Schmalkalden

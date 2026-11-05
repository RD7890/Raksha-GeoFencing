# Raksha GeoFencing

Safe-zone app: set your home on a satellite map, pick a radius (30–500 m, default 50 m) and get a
**full-screen alert** when you leave the circle.

## How it works
1. **Outer ring (Play Services geofence, 200 m+)** – only a wake-up trigger, cheap on battery.
2. **ENTER outer ring → `TrackingService`** (foreground, location type) streams GPS fixes every ~3 s
   and compares them with the exact safe-zone radius.
3. **Exit confirmed** (2 consecutive fixes outside `radius + min(accuracy, 10 m)`, fixes worse than
   30 m ignored) → alarm notification with a **full-screen intent** → `AlertActivity` opens over the
   lock screen with sound + vibration. If the phone is unlocked and in use, Android shows a
   heads-up notification instead.
4. Leaving the outer ring without a confirmed exit also raises the alert; coming back re-arms the zone.
5. Boot / app update re-registers the geofences.

## Map
MapLibre + MapTiler hybrid (satellite + labels). Key lookup order: `local.properties`
(`maptiler.key=...`) → env `MAPTILER_KEY` → default in `app/build.gradle.kts`.
MapTiler's free plan is non-commercial and has a monthly request quota.

## Permissions to allow on the phone
Location "Allow all the time", notifications, full-screen notifications (Android 14+ may need
this in settings), battery "Unrestricted". On Xiaomi/Oppo/Vivo also enable Autostart and
"Show on lock screen". Use **Settings → Test alert in 10 seconds** to verify the alert on your phone.

## Build
1. `local.properties` with your Android SDK path (and optionally `maptiler.key`).
2. `./gradlew assembleDebug` (or the GitHub Actions workflow).

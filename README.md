# Raksha GeoFencing

Safe-zone app: set your home on a satellite map, pick a radius (30–500 m, default 50 m) and get a
**full-screen alert** when you leave the circle.

## How it works
1. **`TrackingService`** (foreground, location type) starts as soon as a safe zone is enabled (app open,
   boot, geofence backup). Near a zone it streams high-accuracy GPS every ~3 s and compares each fix with
   the exact radius; far away it drops to balanced power (every 30 s).
2. **Outer ring (Play Services geofence, 200 m+)** – backup wake-up / restart trigger only.
3. **Exit confirmed** (2 consecutive fixes outside `radius + min(accuracy, 10 m)`, fixes worse than
   30 m ignored) → alarm notification with a **full-screen intent** → `AlertActivity` opens over the
   lock screen with sound + vibration. If the phone is unlocked and in use, Android shows a
   heads-up notification instead.
4. Leaving the outer ring without a confirmed exit also raises the alert; coming back re-arms the zone.
5. Boot / app update re-registers the geofences.

## v1.4 changes (redesign)
- New "Pearl" look ported from Raksha Redesigned: white + soft grey cards, **#FF2541** primary, Phosphor icons, Raksha font.
- Full-screen map on every map screen with a **floating top bar** and a **floating pill bottom nav** (Map / Zones / Settings).
- **Set Home to my location**: one tap on the map card takes a fresh GPS fix and creates (or moves) the "Home" zone with a **30 m** radius. No dragging.
- Full-screen **Set safe zone** screen: floating search pill, pin in the middle of the free map area, name chips (Home / Work / School / Custom), radius slider with 30/50/100/200 m shortcuts, "My location" button.
- Zones tab with live Inside / Outside pills, pause switch and delete confirmation; tapping a zone flies the map to it.
- Settings tab and permission screens restyled. Added INTERNET permission explicitly (map tiles and place search).

## v1.3 changes (live location + alerts)
- Tracking now has **no distance filter** near a zone, so fixes (and the Live status card) update every ~3 s even when standing still.
- **Watchdog**: if no fix arrives for 30 s the location request is re-registered, and after 3 stalls the app switches between Google fused location and the plain Android GPS/network provider. Phones without Google Play Services use the Android provider from the start.
- **Alert channel renamed** (`raksha_alert_v2`). Android never updates a notification channel after it has been created, so an install upgraded from an older build could keep a silent / low-importance alert channel.
- Alerts check that notifications and the alert channel are enabled; the reason is shown on the Live status card.
- Zones **arm** when the GPS error circle touches the safe zone (before: the reported position itself had to be inside), which matters for small zones and indoor GPS drift.
- Live status card shows service heartbeat, location source, last error and last alert result. Main map has a "follow my location" button.

## Map
MapLibre + MapTiler hybrid (satellite + labels). Key lookup order: `local.properties`
(`maptiler.key=...`) → env `MAPTILER_KEY` → default in `app/build.gradle.kts`.
MapTiler's free plan is non-commercial and has a monthly request quota.

## Permissions to allow on the phone
Location "Allow all the time", notifications, full-screen notifications (Android 14+ may need
this in settings), battery "Unrestricted". On Xiaomi/Oppo/Vivo also enable Autostart and
"Show on lock screen". Use **Settings → Test alert in 10 seconds** and the **Live status** card (shows INSIDE/OUTSIDE, distance, GPS accuracy) to verify the alert on your phone.

## Build
1. `local.properties` with your Android SDK path (and optionally `maptiler.key`).
2. `./gradlew assembleDebug` (or the GitHub Actions workflow).

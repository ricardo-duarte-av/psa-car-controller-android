# psa-car-controller-android

Android client for [PSA Car Controller](https://github.com/flobz/psa_car_controller) (PSACC), the
self-hosted daemon that talks to the PSA group's connected-car cloud (Peugeot, Citroën, DS, Opel,
Vauxhall).

PSACC has no authentication of its own, so the app expects it behind a reverse proxy with HTTP basic
auth. You still sign in to your PSA account in PSACC's own web interface; the app then uses
PSACC's [HTTP API](https://github.com/flobz/psa_car_controller/blob/master/docs/psacc_api.md).

## Features

- **Car**: battery ring and electric range, fuel level and range (hybrids), charging state, odometer,
  outside temperature, 12 V battery, battery health, doors, ignition, last location (with address and
  *Open in Maps*). Pull to refresh asks PSACC to query PSA live; the cache is re-read every minute
  while the screen is open.
- **Controls**: lock/unlock, climate (preconditioning), horn, lights, wake the car for fresh data.
  Disruptive commands ask for confirmation.
- **Charging controls**: start/stop charging, scheduled charge start, and PSACC's charge limit and
  stop time (when charge control is enabled on the server).
- **Trips**: history grouped by day, distance-weighted consumption, per-trip detail with a route sketch.
- **Charging history**: sessions with start/end level, energy, cost and CO₂.
- Material 3 Expressive UI with dynamic color; adaptive navigation (bar / rail / drawer).

## Build

Toolchain: AGP 9.3, Kotlin 2.4, compileSdk/targetSdk 37, minSdk 29, JDK 17 target.
Versions live in `gradle/libs.versions.toml`.

```
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest
```

`local.properties` (gitignored) must point `sdk.dir` at an Android SDK with `platforms/android-37`
and `build-tools/37.0.0`.

Release builds are minified with R8 and signed only when `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS` and `KEY_PASSWORD` are set in the environment.

## Releases

Pushing a `vX.Y.Z` tag builds the signed release, publishes it to the Google Play internal testing
track and creates a GitHub Release with debug and release APKs. Other pushes only build.

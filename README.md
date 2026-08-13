
<img src="https://github.com/chr0nzz/traefik-manager/raw/main/docs/public/images/mobile-banner.png" alt="Traefik Manager">

# Traefik Manager Mobile

Native Android companion app for [Traefik Manager](https://github.com/chr0nzz/traefik-manager) - manage your Traefik routes, middlewares, services and CrowdSec decisions from your phone.

> **Requires Traefik Manager v1.10.1 or higher.**

---

## Download

| Platform         | Link                                                                                |
| ------------------| -------------------------------------------------------------------------------------|
| Android (APK)    | [Latest release](https://github.com/chr0nzz/traefik-manager-mobile/releases/latest) |
| Google Play | [Install from Play Store](https://play.google.com/store/apps/details?id=dev.chr0nzz.traefikmanager)                         |

---

## Screenshots

<!-- Placeholder: these are still v1 shots. Retake against 2.0 before release. -->
<div align="center">
  <img src="https://traefik-manager.xyzlab.dev/images/dark-mobile-connect.png"       alt="Connect"     width="130" />
  <img src="https://traefik-manager.xyzlab.dev/images/dark-mobile-dashboard.png"     alt="Overview"    width="130" />
  <img src="https://traefik-manager.xyzlab.dev/images/dark-mobile-routes.png"        alt="Routes"      width="130" />
  <img src="https://traefik-manager.xyzlab.dev/images/dark-mobile-middleware.png"    alt="Middlewares"  width="130" />
  <img src="https://traefik-manager.xyzlab.dev/images/dark-mobile-services-live.png" alt="Services"    width="130" />
  <img src="https://traefik-manager.xyzlab.dev/images/dark-mobile-settings.png"      alt="Settings"    width="130" />
</div>
<div align="center">
  <sub>Connect &nbsp;·&nbsp; Overview &nbsp;·&nbsp; Routes &nbsp;·&nbsp; Middlewares &nbsp;·&nbsp; Services &nbsp;·&nbsp; Settings</sub>


[View light & dark screenshots in the docs](https://traefik-manager.xyzlab.dev/ui-examples.html)
</div>


## Features

- **Overview** - the signal desk: routers, services and middlewares as cards with health, provider breakdown and entry points
- **Routes** - view, enable/disable, add, edit and delete HTTP/TCP/UDP routes, multiple domains per route, raw YAML editing
- **Middlewares** - 24 templates with guided wizards, plus edit, delete and htpasswd generation
- **Services** - health, backends up, provider filter and the routers each one serves
- **Logs** - live tail with CLF and JSON parsing, tappable facet filters and the v1.10 analytics cards
- **CrowdSec** - decisions and alerts, add and delete bans, the full stat desk and a world map
- **Certificates and plugins** - expiry thresholds and the installed plugin list
- **Backups** - dynamic and static config backups, restore, and Git backup status, history, diff and push
- **Multi-server** - switch between the host and any agent from the drawer; tabs follow what each server actually runs
- **Home screen widgets** - the same desk cards in three sizes, configured per widget
- **App lock** - optional biometric unlock, with relock when the app goes to the background

---

## Requirements

| Requirement              | Version                     |
| ------------------------- | ---------------------------- |
| Traefik Manager (server) | **v1.10.1 or higher**       |
| Android                  | 13+ (API 33)                |
| JDK (to build)           | 17                          |

---

## Getting Started

### 1. Generate an API key in Traefik Manager

In the Traefik Manager web UI go to **Settings → Authentication** and generate an API key. Copy it - you will need it during app setup.

### 2. Configure the app

On the **Settings** tab in the mobile app enter:

- **Server URL** - the base URL of your Traefik Manager instance, e.g. `https://traefik-manager.example.com`
- **API Key** - the key generated in step 1

Tap **Save** and the app will connect immediately.

---

## Building from Source

```bash
# Debug build
./gradlew :app:assembleDebug

# Unit tests
./gradlew :app:testDebugUnitTest

# Signed release APK and Play bundle
./gradlew :app:assembleRelease :app:bundleRelease
```

Release builds are signed from a `keystore.properties` at the repo root, which is gitignored:

```properties
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without that file the release build still compiles, unsigned. CI signs from repository secrets
(`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`)
and refuses to publish anything signed with the wrong certificate, since a mismatch would break
in-place upgrades for everyone who already has the app.

---

## Tech Stack

- Kotlin 2.4 · [Jetpack Compose](https://developer.android.com/compose) with Material 3 Expressive
- [Hilt](https://dagger.dev/hilt/) for dependency injection
- [Retrofit](https://square.github.io/retrofit/) 3 · OkHttp 5 · kotlinx.serialization
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) with an encrypted key store
- [Glance](https://developer.android.com/jetpack/androidx/releases/glance) and WorkManager for the widgets
- Robolectric and JUnit for tests, R8 with resource shrinking for release builds

> Material 3 is pinned to an alpha for the Expressive components. It is re-checked each release;
> the screenshot tests are the regression net, and the pin can be dropped back to the last stable
> if an alpha regresses.

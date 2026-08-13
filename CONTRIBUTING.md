# Contributing to Traefik Manager Mobile

Thanks for your interest in contributing. This guide covers everything you need to get started.

This repository is the **Android app**. For the server it talks to, see [chr0nzz/traefik-manager](https://github.com/chr0nzz/traefik-manager).

---

## Table of Contents

- [Reporting bugs](#reporting-bugs)
- [Suggesting features](#suggesting-features)
- [Submitting a pull request](#submitting-a-pull-request)
- [Running locally](#running-locally)
- [Tests](#tests)
- [Project structure](#project-structure)
- [Code style](#code-style)
- [Branch guide](#branch-guide)

---

## Reporting bugs

Please use the [Bug Report](.github/ISSUE_TEMPLATE/bug_report.yml) issue template. Include:

- App version and where you installed it from
- The Traefik Manager server version it connects to
- Android version, device, and whether it is a phone or tablet
- How the app reaches your server - public HTTPS, local IP, VPN, self-signed certificate
- Steps to reproduce, and what you expected instead

If the bug is in Traefik Manager rather than the app, open it on the [server repo](https://github.com/chr0nzz/traefik-manager/issues) instead.

For **security vulnerabilities**, do not open a public issue - see [SECURITY.md](SECURITY.md).

---

## Suggesting features

Open a [Feature Request](.github/ISSUE_TEMPLATE/feature_request.yml) issue before writing any code. This lets us discuss the idea first and avoids wasted effort if it doesn't fit the app's direction.

---

## Submitting a pull request

1. Fork the repo and create your branch from `main`.
2. Keep PRs focused - one fix or feature per PR.
3. For anything beyond a small bug fix, open an issue first so we can align on approach.
4. Test against a real Traefik Manager instance, on a phone *and* a tablet if the change touches layout.
5. Run the unit tests before opening the PR.
6. Do not bump `versionCode` or `versionName` - releases handle that.

---

## Running locally

### Requirements

- **JDK 17** (the CI build uses Temurin 17)
- Android Studio, or the Android SDK with `compileSdk 37` installed
- A running Traefik Manager server, v1.10.1 or newer
- An API key from that server - **Settings - Authentication - API Keys**

The app targets **Android 13 (API 33) and above**.

### Setup

```bash
git clone https://github.com/chr0nzz/traefik-manager-mobile.git
cd traefik-manager-mobile
git checkout main
```

Create `local.properties` with your SDK location if Android Studio has not already:

```properties
sdk.dir=/path/to/Android/Sdk
```

`local.properties` and `keystore.properties` are gitignored. Never commit either.

### Build and run

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:installDebug           # build and install on a connected device
```

Debug builds use the standard Android debug keystore that Gradle generates in `~/.android/`, so no signing setup is needed to develop. The release signing config only activates when a `keystore.properties` is present, which is why a fork builds debug out of the box.

### Connecting to a server

On first launch the app asks for your server URL and API key. For a local server over plain HTTP, use `http://192.168.x.x:5000` - cleartext and user-installed CA certificates are permitted deliberately, because homelab servers are often on private IPs with self-signed certificates.

---

## Tests

```bash
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:lint                   # Android lint
```

Please run the unit tests before opening a pull request, and add a test when you change API parsing or anything in `data/`. The API contract is the most common source of breakage, because the app has to tolerate several server versions at once.

---

## Project structure

```
app/src/main/java/dev/chr0nzz/traefikmanager/
    MainActivity.kt           # Single activity, Compose host
    TmApplication.kt          # Application class
    data/                     # API client, models, repositories, storage
    di/                       # Dependency injection wiring
    ui/                       # Compose screens, components, theme
    widget/                   # Home screen widgets
app/src/test/                 # Unit tests
gradle/libs.versions.toml     # Dependency versions - single source of truth
.github/workflows/
    build-android.yml         # Signed release build, runs on a v* tag
```

---

## Code style

- **Kotlin:** follow the existing style - explicit types on public API, trailing commas, no wildcard imports.
- **Compose:** stateless composables where possible, state hoisted to the screen or view model. No business logic inside composables.
- **Dependencies:** add versions to `gradle/libs.versions.toml`, never inline in a build file.
- **No comments:** don't add explanatory comments to code - use clear names instead. The existing codebase follows this convention.
- **No dead code:** don't leave commented-out blocks or unused variables.

---

## Branch guide

| Branch | Purpose | Accepts PRs? |
|--------|---------|--------------|
| `main` | The current app - version 2.x, Kotlin and Jetpack Compose | **Yes - every PR targets `main`** |
| `v1` | The retired 1.x React Native app, preserved for anyone still running it | No - archived, not maintained |

Version 2.0 is a ground-up rewrite in Kotlin and Jetpack Compose. The old React Native app stays on `v1` so existing users can still build it, but it receives no fixes. Pull requests and issues should assume 2.x unless they explicitly say otherwise.

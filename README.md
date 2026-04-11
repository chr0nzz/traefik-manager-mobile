
<img src="https://github.com/chr0nzz/traefik-manager/raw/main/docs/images/mobile-banner.png" alt="Traefik Manager">

# Traefik Manager Mobile

Companion mobile app for [Traefik Manager](https://github.com/chr0nzz/traefik-manager) - manage your Traefik routes, middlewares, and services from your phone.

> **Requires Traefik Manager v0.6.0 or higher.**
> The mobile app authenticates via the API key feature introduced in v0.6.0. Earlier versions are not supported.
>
> **Mobile app v0.6.0+ requires Traefik Manager v0.10.0 or higher.**
> v0.10.0 includes the `CONFIG_DIR` multi-file API changes that the mobile app depends on.

---

## Download

| Platform         | Link                                                                                |
| ------------------| -------------------------------------------------------------------------------------|
| Android (APK)    | [Latest release](https://github.com/chr0nzz/traefik-manager-mobile/releases/latest) |
| Google Play Beta | [Sign up to beta test](https://forms.gle/csituqc92sreNooZ8)                         |
| iOS              | Build from source (see below)                                                       |

---

## Screenshots

<div align="center">
  <img src="https://github.com/chr0nzz/traefik-manager/raw/main/docs/images/dark-mobile-connect-1.png"    alt="Connect"     width="150" />
  <img src="https://github.com/chr0nzz/traefik-manager/raw/main/docs/images/dark-mobile-dashboard-2.png"  alt="Dashboard"   width="150" />
  <img src="https://github.com/chr0nzz/traefik-manager/raw/main/docs/images/dark-mobile-routes-3.png"     alt="Routes"      width="150" />
  <img src="https://github.com/chr0nzz/traefik-manager/raw/main/docs/images/dark-mobile-middleware-4.png" alt="Middlewares"  width="150" />
  <img src="https://github.com/chr0nzz/traefik-manager/raw/main/docs/images/dark-mobile-services-5.png"   alt="Services"    width="150" />
  <img src="https://github.com/chr0nzz/traefik-manager/raw/main/docs/images/dark-mobile-settings-6.png"   alt="Settings"    width="150" />
</div>
<div align="center">
  <sub>Connect &nbsp;·&nbsp; Dashboard &nbsp;·&nbsp; Routes &nbsp;·&nbsp; Middlewares &nbsp;·&nbsp; Services &nbsp;·&nbsp; Settings</sub>
</div>

[View light & dark screenshots in the docs](https://traefik-manager.xyzlab.dev/ui-examples.html)

---

## Features

- **Routes** - view, enable/disable, add, edit, and delete HTTP/TCP/UDP routes; assign multiple domains per route
- **Middlewares** - view, add, edit, and delete middlewares with 12 built-in templates (HTTPS redirect, basic auth, rate limit, forward auth, and more)
- **Services** - live service overview with health status, provider, and linked routers
- **Edit mode** - toggle edit mode to reveal enable/disable toggles, edit, and delete actions on cards
- **insecureSkipVerify** - per-route toggle for backends with self-signed certificates
- **System theme** - follows iOS/Android system light/dark preference automatically
- **Secure storage** - server URL and API key stored in device secure storage

---

## Requirements

| Requirement | Version |
|---|---|
| Traefik Manager (server) | **v0.10.0 or higher** (mobile v0.6.0+), v0.6.0+ for earlier mobile versions |
| Expo SDK | 54 |
| React Native | 0.81 |
| Android | 7.0+ (API 24+) |
| iOS | 16+ |

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
# Install dependencies
npm install

# Start the Expo dev server
npm start

# Run on Android
npm run android

# Run on iOS
npm run ios
```

### Production builds (EAS)

```bash
# Android preview APK
npm run build:preview

# Android production AAB
npm run build:prod
```

---

## Tech Stack

- [Expo](https://expo.dev) SDK 54 / React Native 0.81
- [Expo Router](https://expo.github.io/router) v6 (file-based navigation)
- [TanStack Query](https://tanstack.com/query) v5 (data fetching & caching)
- [Zustand](https://zustand-demo.pmnd.rs) (theme state)
- [React Native Paper](https://reactnativepaper.com) (surface components)
- [MaterialCommunityIcons](https://materialdesignicons.com) (icons)

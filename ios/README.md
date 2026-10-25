# Traefik Manager for iOS

Native SwiftUI port of the Android app. iOS 17+, iPhone and iPad.

## Build

Open `TraefikManager.xcodeproj` in Xcode 26 and run the `TraefikManager` scheme, or:

```sh
xcodebuild -project TraefikManager.xcodeproj -scheme TraefikManager \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build test
```

The project uses folder-synchronized groups: drop a Swift file anywhere under `TraefikManager/`
and it is part of the app target, no project edits needed.

Debug builds accept `-tm-demo` (open demo mode) and `-tm-reset` (forget the saved connection)
as launch arguments.

## Layout

| Folder | Android counterpart |
| --- | --- |
| `App/` | `ui/nav` – app state, tab bar / sidebar shell, destinations |
| `Data/API/` | `data/api` – HTTP client, agent proxy, demo fixtures |
| `Data/Models/` | `data/model` |
| `Data/Repos/` | `data/repo` |
| `Data/Store/` | `data/store` – Keychain, preferences |
| `Features/` | `ui/<screen>` |
| `UI/` | `ui/theme`, `ui/components` |

## TestFlight

```sh
ASC_KEY_ID=... ASC_ISSUER_ID=... scripts/testflight.sh
```

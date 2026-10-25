# iOS media kit

Everything App Store Connect asks for on the first submission of Traefik Manager 2.5.0 for iOS.

| | |
|---|---|
| App | Traefik Manager, ASC app id `6826725061` |
| Bundle id | `dev.chr0nzz.traefikmanager` (widgets: `dev.chr0nzz.traefikmanager.widgets`) |
| Version | 2.5.0, matching the Android release |
| Platforms | iPhone and iPad, iOS 17 or newer |
| Primary category | Developer Tools |
| Secondary category | Utilities |
| Price | Free |

## Text

All in `metadata/en-US/`. Character limits are App Store Connect's.

| File | Field | Limit | Used |
|---|---|---|---|
| `name.txt` | Name | 30 | 15 |
| `subtitle.txt` | Subtitle | 30 | 26 |
| `promotional_text.txt` | Promotional text, can change without review | 170 | 166 |
| `description.txt` | Description | 4000 | ~2400 |
| `keywords.txt` | Keywords, comma separated, no spaces after commas | 100 | 97 |
| `release_notes.txt` | What's New in This Version | 4000 | ~290 |
| `support_url.txt` | Support URL | | GitHub issues |
| `marketing_url.txt` | Marketing URL | | mobile docs |
| `privacy_url.txt` | Privacy Policy URL | | Privacy section of the mobile docs |

The name, subtitle and keywords together are what search indexes, so no word is repeated across them.

## Screenshots

The store screenshots are too heavy for git, so they live next to the release builds in `~/code/traefik-manager/release-builds/ios-2.5.0/media-kit/screenshots/`. They come straight from the simulator in demo mode, with a 9:41 status bar and no demo banner.

| Folder | Device class | Size |
|---|---|---|
| `iphone-6.9-light`, `iphone-6.9-dark` | iPhone 6.9" (17 Pro Max) | 1320 × 2868 |
| `ipad-13-light`, `ipad-13-dark` | iPad 13" (Pro M5) | 2064 × 2752 |

App Store Connect takes up to 10 per device class and scales the 6.9" and 13" sets down for the smaller devices. Upload one theme per class, in file order; the first three are what most people see in search, so they lead with the dashboard, routes and route detail. Leave out `04-route-add` and `13-notifications` if you need to get to 10.

Regenerate them on the Mac with `~/dev/tm-sync/storeshots.sh "<simulator>" <prefix>`, which launches each screen with `-tm-demo -tmShots clean -tmTab …`.

## App icon

`icon-1024.png` is the 1024 × 1024 marketing icon from the asset catalog. App Store Connect reads it from the build, so it is here for press and listings only.

## App Privacy

Answer **Data Not Collected**. The app talks only to the Traefik Manager server the user enters. The API key is kept in the iOS Keychain and the server URL in the app's preferences, both on the device. There is no analytics, crash reporting, advertising or tracking SDK, and no account with the developer.

| Question | Answer |
|---|---|
| Do you or your third-party partners collect data from this app? | No |
| Tracking | No |

## Age rating

Every content question is **None** or **No**. Unrestricted web access: **No**; the app opens route URLs in the browser but does not browse the web itself. Result: **4+**.

## Export compliance

`ITSAppUsesNonExemptEncryption` is `NO` in the build: the app only uses HTTPS through the system networking stack, which is exempt.

## App Review

Paste `review-notes.txt` into **App Review Information → Notes**. No sign-in is needed: **Try Demo** on the connect screen runs the whole app against built-in sample data. Leave the demo account fields empty and untick **Sign-in required**.

Contact details in App Review Information are the developer's own and are not stored here.

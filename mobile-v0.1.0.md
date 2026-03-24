## traefik-manager-mobile v0.1.0

Initial release of the Traefik Manager companion app.

> Requires **Traefik Manager v0.5.0 or higher** (API key authentication introduced in v0.5.0).

---

### Routes

- List all HTTP, TCP, and UDP routes with status, domain, target, and attached middlewares
- Enable / disable routes with a toggle — configuration is preserved, Traefik stops routing until re-enabled
- Add new routes via a form with name, host/domain, target IP, port, protocol picker, and middleware list
- Edit existing routes in a bottom sheet
- Delete routes with a confirmation prompt
- Tap the domain chip to open the route URL in the browser

### Middlewares

- List all middlewares with type badge, protocol badge, and YAML config preview
- Add new middlewares with a two-step flow:
  1. Choose from 12 built-in templates (Blank, HTTPS Redirect, Basic Auth, Security Headers, Rate Limit, Forward Auth, Strip Prefix, Add Prefix, Compress, IP Allowlist, Redirect Regex, Chain)
  2. Set a name and review / edit the pre-filled YAML config
- Edit existing middlewares (name + YAML)
- Delete middlewares with a confirmation prompt

### Services

- Live service list with protocol badge, type badge, and colour-coded status chip (Success / Warning / Error)
- Server health displayed as a fraction (e.g. `2/3 active`)
- Provider chip with icon
- Linked router chips (up to 3 shown, overflow count displayed)
- Tap the info icon for a full detail sheet

### Edit mode

- Tap the pencil icon in the top bar to enter edit mode
- In edit mode, cards reveal: **toggle** (routes only), **edit**, and **delete** buttons
- Edit mode button is highlighted in orange when active
- Buttons are hidden when not in edit mode to keep the list clean

### Settings

- Server URL and API key configuration with connection test
- Appearance: Light, Dark, or Follow System (follows iOS/Android system preference)

### General

- Automatic light/dark theme following system preference via `userInterfaceStyle: automatic`
- Credentials stored in device secure storage (Expo SecureStore)
- Pull-to-refresh on all list tabs
- Keyboard-safe bottom sheet modals on both iOS and Android

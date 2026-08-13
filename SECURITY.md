# Security Policy

## Supported Versions

Only the latest release receives security fixes.

| Version | Supported |
|---------|-----------|
| 2.x (Kotlin) | Yes |
| 1.x (React Native) | No - preserved on the `v1` branch for anyone still running it, but not maintained |

## Reporting a Vulnerability

Please **do not** open a public GitHub issue for security vulnerabilities.

**Preferred:** Use [GitHub private vulnerability reporting](https://github.com/chr0nzz/traefik-manager-mobile/security/advisories/new) - this keeps the report confidential until a fix is released.

**Alternative:** Email [187675356+chr0nzz@users.noreply.github.com](mailto:187675356+chr0nzz@users.noreply.github.com) with a description of the issue and steps to reproduce.

Please include:
- A description of the vulnerability and its potential impact
- Steps to reproduce or proof-of-concept
- The app version, Android version and device
- Whether it needs a rooted device, a hostile app installed alongside, or physical access

You can expect an acknowledgement within **48 hours** and a fix or mitigation plan within **14 days** depending on severity.

## Scope

This policy covers the Android app. For the server, see the [Traefik Manager security policy](https://github.com/chr0nzz/traefik-manager/security/policy).

In scope:
- API keys or credentials readable by another app, or written to logs, crash reports or the clipboard without intent
- Data left readable in device backups or accessible without the app lock
- Bypassing biometric or PIN app lock
- Certificate validation weaknesses beyond the documented behaviour below
- Exported components, deep links or intents that let another app read data or act on your behalf
- Any way for a third party on the same network to read or alter traffic between the app and your server over HTTPS

Out of scope:
- **Cleartext HTTP and user-installed CA certificates.** These are permitted deliberately. The app is built for homelabs where the server is often on a private IP with a self-signed or private-CA certificate. Choosing `http://` is the user's decision.
- Anything requiring a rooted or already-compromised device
- Anything requiring physical access to an unlocked device
- Vulnerabilities in Traefik Manager itself - report those to the [server repo](https://github.com/chr0nzz/traefik-manager/security/advisories/new)
- Vulnerabilities in Traefik - report those to the [Traefik project](https://github.com/traefik/traefik/security)
- Reports that a stolen API key grants access. Keys are bearer credentials by design; revoke them in **Settings - Authentication - API Keys** on the server.

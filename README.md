# GhostGuard — System-wide ad blocker for Android

<div align="center">

[![Platform](https://img.shields.io/badge/platform-Android_7.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/language-Kotlin-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Core](https://img.shields.io/badge/tunnel-Go-00ADD8?logo=go&logoColor=white)](https://go.dev)
[![License](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)
[![Release](https://img.shields.io/github/v/release/alzimerahmed/GhostGuard)](https://github.com/alzimerahmed/GhostGuard/releases)
[![Downloads](https://img.shields.io/github/downloads/alzimerahmed/GhostGuard/total)](https://github.com/alzimerahmed/GhostGuard/releases)

*Free, open-source ad, tracker, and malware blocking for every app on your device — through a local VPN, with no root and no data collection.*

[Download](https://github.com/alzimerahmed/GhostGuard/releases) • [Features](#features) • [How It Works](#how-it-works) • [Building](#building) • [Contributing](#contributing)

</div>

---

## Features

**Filtering**

- System-wide DNS filtering via a local VPN — no root required — or via iptables in Root Proxy mode
- Optional HTTPS filtering with a userspace TCP/IP stack: per-app MITM, cosmetic CSS rules, and EasyList/AdGuard JS scriptlets
- Multiple built-in filter lists (StevenBlack, AdGuard DNS, EasyList, and more) with automatic updates on a 6/12/24/48-hour schedule
- Custom block, allow, and whitelist rules
- Blocks phishing, malware, and malvertising domains
- Region-aware defaults that auto-enable filters for your language
- A curated 284-domain passthrough list keeps banking, payment, and government apps working

**Control and visibility**

- Real-time DNS query logs with search and filtering
- Per-app filtering — bypass the VPN for selected apps, and scope HTTPS filtering to chosen browsers
- DNS-over-HTTPS (DoH) with multiple providers
- WireGuard profile import
- Certificate install verification that auto-refreshes after you return from Android Settings

**Experience**

- Material 3 interface with dynamic color, dark/light/system themes, and 7 accent colors
- Quick Settings tile and home screen widget
- Auto-reconnect on boot, settings backup export/import
- Optional, opt-in crash reporting; manual local log export
- Available in English, Vietnamese, Japanese, Korean, Chinese, Thai, and Spanish

All filtering happens on your device. DNS logs, filter lists, and the MITM certificate key never leave it.

---

## How It Works

In VPN mode, GhostGuard routes DNS queries through a local VpnService; in Root Proxy mode, iptables redirects them instead. Queries are matched against loaded filter lists using a memory-efficient Trie, and matching domains are resolved to a block response locally — all other traffic passes through untouched.

When HTTPS filtering is enabled, a userspace TCP/IP stack (gVisor netstack) terminates each TCP flow in Go and resolves the owning app's UID through `ConnectivityManager.getConnectionOwnerUid()`. Only flows from browsers you select are intercepted. Cert-pinned apps and the curated passthrough list bypass interception cleanly, and cosmetic CSS rules and scriptlets are injected into HTML responses through a small in-memory local asset host.

The MITM root certificate is generated in memory and its private key is wrapped with an Android Keystore key before storage — the plaintext key is never written to disk.

---

## Tech Stack

| Layer | Technology |
|---|---|
| UI | Kotlin, Jetpack Compose, Material 3 (dynamic theming) |
| Navigation | Compose Destinations |
| Dependency injection | Koin |
| Persistence | Room, DataStore |
| Tunnel core | Go (DNS filtering, trie matcher, gVisor netstack MITM, WireGuard) via gomobile AAR |
| Build | Gradle, JDK 17, Android SDK 36 (min SDK 24) |

---

## Project Structure

```
app/                      Android app (Kotlin, Compose)
  src/main/java/app/ghostguard/
    service/              VpnService, tunnel adapter, firewall, root proxy
    security/             Keystore-wrapped MITM CA key management
    data/                 Room entities/DAOs, DataStore preferences
    ui/                   Compose screens (home, logs, filters, settings, ...)
    worker/               Filter-list auto-update workers
tunnel/                   Go tunnel core (gomobile module)
  internal/
    trie/                 Filter-list matching
    mitm/                 HTTPS interception, CA lifecycle, scriptlets
    dns/                  DNS handling, DoH/DoT/DoQ
    wireguard/            WireGuard config parsing
scripts/                  Tunnel build script
fastlane/                 Store metadata and screenshots
```

---

## Building

### Requirements

- Android Studio Ladybug or newer
- JDK 17+
- Android SDK 36 (min SDK 24)
- Go 1.21+ and gomobile (only to rebuild the tunnel AAR — a pre-built copy ships in `app/libs/`)

### Steps

1. Clone and open:

   ```bash
   git clone https://github.com/alzimerahmed/GhostGuard.git
   cd GhostGuard
   ```

2. Open the project in Android Studio, sync Gradle, and run on a device or emulator.

3. Or build from the command line:

   ```bash
   ./gradlew assembleDebug
   ./gradlew bundleRelease   # requires a signing key
   ```

<details>
<summary>Rebuilding the Go tunnel AAR (optional)</summary>

```bash
go install github.com/sagernet/gomobile/cmd/gomobile@latest
export PATH=$PATH:$(go env GOPATH)/bin
gomobile init
./scripts/build_tunnel.sh
```

The output AAR and JAR land in `app/libs/`.
</details>

<details>
<summary>Quality gates</summary>

```bash
./gradlew lint ktlintCheck detekt testDebugUnitTest assembleDebug
```

CI runs the same chain: lint → ktlint → detekt → unit tests → assembleDebug.
</details>

---

## Usage

1. Install the APK and open GhostGuard.
2. Complete the onboarding wizard: grant VPN permission, disable battery optimization, and choose telemetry preferences.
3. Pick filter lists (sensible defaults are pre-selected for your region) and tap start.
4. Optional: enable HTTPS filtering, install the root certificate when prompted, and select which browsers to filter.

---

## FAQ

**Does it work without root?**
Yes. VPN mode covers everything; Root Proxy mode is an optional alternative for rooted devices.

**Will banking apps break?**
The curated passthrough list and cert-pinning detection keep banking, payment, and government apps working. You can also bypass any app individually.

**Does GhostGuard collect my data?**
No. All filtering and logging happen on-device. Crash reporting is opt-in and off by default.

**Why isn't it on Google Play?**
Google's policies prohibit system-wide ad blockers, so GhostGuard is distributed through GitHub Releases.

---

## Contributing

Pull requests and issue reports are welcome. For translations, open an issue or submit a PR — GhostGuard ships in English, Vietnamese, Japanese, Korean, Chinese, Thai, and Spanish.

---

## Roadmap

- [ ] One-tap setup with curated filter packs
- [ ] Signed filter-list downloads
- [ ] Enhanced per-app firewall rules
- [ ] Statistics dashboard

---

## Changelog

See [GitHub Releases](https://github.com/alzimerahmed/GhostGuard/releases).

---

## License

GhostGuard is licensed under the [GNU General Public License v3.0](LICENSE).
Maintained by Alzimer Ahmed.

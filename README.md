[![Qodana](https://github.com/NikitaDmitryuk/ultra-client/actions/workflows/qodana_code_quality.yml/badge.svg)](https://github.com/NikitaDmitryuk/ultra-client/actions/workflows/qodana_code_quality.yml)

# ultra-client

A Kotlin Multiplatform (KMP) mobile client for the [VLESS](https://xtls.github.io/en/config/outbounds/vless.html) protocol, powered by [Xray-core](https://github.com/XTLS/Xray-core). Runs on **Android** and **iOS** from a single shared codebase.

---

## Features

- **VLESS protocol** — Reality, TLS, WebSocket, and gRPC transports
- **Kill Switch** — blocks all traffic if the tunnel drops unexpectedly
- **Fake DNS** — all DNS queries are resolved inside the tunnel, eliminating leaks
- **Random local ports** — local SOCKS and DNS ports are randomised on every connection
- **Split routing** — per-app routing rules (Android); chosen apps connect directly
- **Shared UI** — Compose Multiplatform UI, single codebase for both platforms
- **Offline-first** — profiles and settings stored locally with SQLDelight

---

## Architecture

The project follows Clean Architecture with three Gradle modules inside `shared/`:

```
shared/domain          ← models, repository interfaces, use cases (pure Kotlin)
shared/data            ← SQLDelight DB, VLESS parser, Xray config builder, platform actuals
shared/presentation    ← Compose Multiplatform screens and ScreenModels
androidApp             ← Android application, TUN service, Xray bridge
iosApp                 ← Xcode project, SwiftUI entry, Network Extension
xray-build             ← Go build scripts for XrayCore.aar and LibXray.xcframework
```

### Dependency graph

```
androidApp  ──────────────────────────┐
iosApp      (Xcode, not Gradle)        │
                                       ▼
shared:presentation ──► shared:data ──► shared:domain
```

### Key design decisions

| Concern | Solution |
|---|---|
| Platform tunnel lifecycle | `expect class PlatformVpnEngine` — Android uses `VpnService`, iOS uses `NETunnelProviderManager` |
| Database | SQLDelight with `expect class DatabaseDriverFactory` for `AndroidSqliteDriver` / `NativeSqliteDriver` |
| DI | Koin 4 with `expect val platformDataModule` |
| Navigation | Voyager (`Navigator` + `ScreenModel`) |
| State | Kotlin coroutines + `StateFlow` throughout |
| iOS memory limit | Network Extension target is pure Swift + LibXray, no KMP runtime |

### Data flow — connect

```
HomeScreen → HomeScreenModel.toggleVpn()
  → ConnectVpnUseCase(profileId)
      ├── VpnProfileRepository.getById()   → SQLDelight
      ├── AntiDetectRepository.get()       → SQLDelight
      ├── PortRandomizer.randomSocksPort() → random Int
      ├── XrayConfigBuilder.build()        → JSON string
      └── PlatformVpnEngine.connect()
            ↓ Android
            Intent → UltraVpnService
              ├── TunConfigurator.establish() → ParcelFileDescriptor
              ├── XrayBridge.startXray(json, tunFd)
              └── VpnStateHolder.emit(Connected)
                    → HomeScreenModel reacts → UI updates
```

### Data flow — import profile

```
ProfilesScreen → "Paste" → ProfilesScreenModel.importFromClipboard()
  → ClipboardReader.readText()         → platform clipboard
  → ImportProfileUseCase(rawUrl)
      ├── VlessUrlParser.parse(rawUrl)  → VlessConfig
      └── VpnProfileRepository.insert(VpnProfile)
            → SQLDelight INSERT → observeAll() Flow emits → UI recomposes
```

---

## Module details

### shared:domain

Pure Kotlin — no Android, no Compose, no platform dependencies.

**Models:** `VpnProfile`, `VlessConfig`, `TunnelState`, `SplitTunnelRule`, `AntiDetectConfig`

**Repositories (interfaces):** `VpnProfileRepository`, `AntiDetectRepository`

**Use cases:** `ConnectVpnUseCase`, `DisconnectVpnUseCase`, `ImportProfileUseCase`, `GetTunnelStateUseCase`, `GetProfilesUseCase`, `DeleteProfileUseCase`, `SetActiveProfileUseCase`, `UpdateSplitTunnelUseCase`, `UpdateAntiDetectUseCase`

### shared:data

**VLESS URL parser** (`VlessUrlParser`) — parses `vless://uuid@host:port?params#name`, supports all transport and security parameters.

**Xray config builder** (`XrayConfigBuilder`) — generates complete Xray JSON from `VlessConfig` + `AntiDetectConfig` + random ports. Covers Reality, TLS, WS, gRPC stream settings; FakeDNS injection; routing rules.

**SQLDelight schema** — two tables:

```sql
-- connection profiles
vpn_profiles (id, name, raw_url, config_json, is_active, created_at)

-- singleton anti-detect settings
anti_detect_config (kill_switch_enabled, fake_dns_enabled, random_port_enabled, split_tunnel_json)
```

**Platform actuals:**

| Declaration | Android | iOS |
|---|---|---|
| `PlatformVpnEngine` | Sends `Intent` to `UltraVpnService` | Manages `NETunnelProviderManager` |
| `DatabaseDriverFactory` | `AndroidSqliteDriver` | `NativeSqliteDriver` |
| `platformDataModule` | Koin module with `Context` | Koin module, no context |
| `currentTimeMillis()` | `System.currentTimeMillis()` | `NSDate.timeIntervalSince1970` |

### shared:presentation

Three Voyager screens:

| Screen | Purpose |
|---|---|
| `HomeScreen` | Connection toggle, status indicator, active profile, split-tunnel warning |
| `ProfilesScreen` | Profile list, paste-from-clipboard import, swipe-to-delete |
| `SettingsScreen` | Kill Switch, Fake DNS, Random Ports, per-app routing (Android) |

### androidApp

- `UltraVpnService` — `VpnService` subclass; TUN lifecycle; foreground service; Xray watchdog coroutine
- `TunConfigurator` — `VpnService.Builder` setup: address `10.0.0.1/32`, default routes, DNS, MTU 1500
- `XrayBridge` — reflection-based bridge to `XrayCore.aar` (gomobile output)
- `KillSwitchManager` — re-establishes TUN with no routes to block all traffic
- `SplitTunnelHelper` — calls `addDisallowedApplication()` per rule

### iOS (Xcode)

Two targets:
1. **iosApp** — SwiftUI entry, embeds `SharedPresentation.xcframework`, manages tunnel lifecycle from UI side
2. **NetworkExtension** — `NEPacketTunnelProvider` subclass, imports `LibXray.xcframework` only (no KMP runtime, stays within memory limit)

Config JSON is passed from the main app to the extension at connection time via start options.

---

## Building

### Prerequisites

| Toolchain | Version |
|---|---|
| JDK | 17+ |
| Gradle | 8.11 (wrapper included) |
| Android SDK | API 35, NDK r27+ |
| Xcode | 16+ (macOS only) |
| Go | 1.23+ (for Xray engine build) |

### Android APK

```bash
./gradlew :androidApp:assembleDebug
# → androidApp/build/outputs/apk/debug/app-debug.apk
```

### iOS XCFramework + Xcode build

```bash
# 1. Build the shared KMP framework
./gradlew :shared:presentation:assembleSharedPresentationXCFramework

# 2. Copy into Xcode project
cp -R shared/presentation/build/XCFrameworks/release/SharedPresentation.xcframework \
     iosApp/Frameworks/SharedPresentation.xcframework

# 3. Open iosApp/iosApp.xcodeproj and build in Xcode
```

### Xray engine (XrayCore.aar / LibXray.xcframework)

The Xray engine is not bundled; build it once from source:

```bash
cd xray-build
bash build.sh
# Outputs:
#   androidApp/libs/XrayCore.aar
#   iosApp/Frameworks/LibXray.xcframework
```

Requires Go 1.23+, `gomobile`, Android NDK (for Android), Xcode (for iOS). See `xray-build/build.sh` for the full checklist.

---

## Development

```bash
make setup    # download ktlint binary
make lint     # run ktlint on all Kotlin sources
make format   # auto-fix formatting issues
make test     # run unit tests (JVM target, no emulator needed)
make clean    # clean Gradle build + remove ktlint binary
```

### Running tests directly

```bash
./gradlew :shared:domain:jvmTest   # 4 serialization tests
./gradlew :shared:data:jvmTest     # 18 parser + config builder tests
```

---

## CI

### On every push to `main` / pull request

`.github/workflows/ci.yml` runs:
- **lint** — ktlint check on all Kotlin sources
- **test** — `jvmTest` for `shared:domain` and `shared:data`; HTML reports uploaded as artifacts

### On `v*.*.*` tag push

`.github/workflows/release.yml` runs tests, then:
- **android** — `assembleDebug`, uploads `android-apk-<tag>.apk`
- **ios** — builds XCFramework, then either a simulator build (default) or a signed IPA if `IOS_CERT_BASE64`, `IOS_CERT_PASSWORD`, `IOS_PROVISIONING_PROFILE_BASE64` secrets are set; uploads `ios-ipa-<tag>`

To publish a release:

```bash
git tag v1.2.3
git push origin v1.2.3
```

---

## Tech stack

| Layer | Library | Version |
|---|---|---|
| Language | Kotlin Multiplatform | 2.1.21 |
| UI | Compose Multiplatform | 1.8.2 |
| Navigation | Voyager | 1.1.0-beta03 |
| DI | Koin | 4.1.1 |
| Database | SQLDelight | 2.2.1 |
| Async | Kotlin Coroutines | 1.10.2 |
| Serialization | kotlinx.serialization | 1.8.1 |
| Network engine | Xray-core / libXray | latest |
| Android Gradle Plugin | AGP | 8.7.3 |
| Lint | ktlint | 1.5.0 |

---

## License

See [LICENSE](LICENSE).

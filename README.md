[![Qodana](https://github.com/NikitaDmitryuk/ultra-client/actions/workflows/qodana_code_quality.yml/badge.svg)](https://github.com/NikitaDmitryuk/ultra-client/actions/workflows/qodana_code_quality.yml)

# ultra-client

A Kotlin Multiplatform (KMP) client for the VLESS protocol, powered by [sing-box](https://sing-box.sagernet.org/). Runs on **Android**, **desktop JVM**, and shared KMP foundations for future Apple targets.

---

## Features

- **VLESS protocol** — Reality, TLS, WebSocket, and gRPC transports
- **Per-app VPN (Android)** — you choose which apps use the tunnel (`addAllowedApplication`); the rest use the normal connection
- **Tunnel DNS** — DNS is handled inside the sing-box TUN path by default
- **Shared UI** — Compose Multiplatform UI, single codebase for both platforms
- **Offline-first** — profiles and settings stored locally with SQLDelight

---

## Architecture

The project follows Clean Architecture with three Gradle modules inside `shared/`:

```
shared/domain          ← models, repository interfaces, use cases (pure Kotlin)
shared/data            ← SQLDelight DB, VLESS parser, sing-box config builder, platform actuals
shared/presentation    ← Compose Multiplatform screens and ScreenModels
androidApp             ← Android application, TUN service, sing-box bridge
desktopApp             ← Compose Desktop shell
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
| Network engine | sing-box is the only production runtime |

### Data flow — connect

```
HomeScreen → HomeScreenModel.toggleVpn()
  → ConnectVpnUseCase(profileId)
      ├── VpnProfileRepository.getById()   → SQLDelight
      ├── AntiDetectRepository.get()       → SQLDelight
      ├── SingBoxConfigBuilder.build()     → JSON string
      └── PlatformVpnEngine.connect()
            ↓ Android
            Intent → UltraVpnService
              ├── TunConfigurator.establish() → ParcelFileDescriptor
              ├── SingBoxBridge.start(json, tunFd)
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

**Models:** `VpnProfile`, `VlessConfig`, `TunnelState`, `VpnAppRouteRule`, `AntiDetectConfig`

**Repositories (interfaces):** `VpnProfileRepository`, `AntiDetectRepository`

**Use cases:** `ConnectVpnUseCase`, `DisconnectVpnUseCase`, `ImportProfileUseCase`, `GetTunnelStateUseCase`, `GetProfilesUseCase`, `DeleteProfileUseCase`, `SetActiveProfileUseCase`, `UpdateVpnIncludedAppsUseCase`, `UpdateAntiDetectUseCase`

### shared:data

**VLESS URL parser** (`VlessUrlParser`) — parses `vless://uuid@host:port?params#name`, supports all transport and security parameters.

**sing-box config builder** (`SingBoxConfigBuilder`) — generates complete sing-box JSON from `VlessConfig` + `AntiDetectConfig`. Covers Reality, TLS, WS, gRPC transport settings; TUN inbound; FakeIP DNS; routing rules.

**SQLDelight schema** — two tables:

```sql
-- connection profiles
vpn_profiles (id, name, raw_url, config_json, is_active, created_at)

-- singleton routing/DNS compatibility settings
anti_detect_config (kill_switch_enabled, fake_dns_enabled, random_port_enabled, split_tunnel_json — JSON `VpnAppRouteRule[]` with `throughVpn`, legacy rows used `isExcluded` / bypass list)
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
| `HomeScreen` | Connection toggle, status indicator, active profile |
| `ProfilesScreen` | Profile list, paste-from-clipboard import, swipe-to-delete |
| `SettingsScreen` | Choose apps that use VPN on Android |

### androidApp

- `UltraVpnService` — `VpnService` subclass; TUN lifecycle; foreground service; sing-box watchdog coroutine
- `TunConfigurator` — `VpnService.Builder` setup: address `10.0.0.1/32`, default routes, DNS, MTU 1500
- `SingBoxBridge` — reflection-based bridge to `SingBoxCore.aar` / mobile binding
- Per-app VPN — `TunConfigurator` calls `addAllowedApplication()` for each app marked for the tunnel (`VpnAppRouteRule`)

### Desktop

`desktopApp` reuses the shared UI and data layers. The JVM VPN engine writes a sing-box config and starts a bundled/system `sing-box` binary. Production packaging should install a privileged helper/service for TUN/routes/DNS on macOS, Windows, and Linux.

---

## Building

### Prerequisites

| Toolchain | Version |
|---|---|
| JDK | 17+ |
| Gradle | 8.11 (wrapper included) |
| Android SDK | API 35, NDK r27+ |
| Xcode | 16+ (macOS only) |
| Go | 1.24.7+ for sing-box 1.13 mobile/desktop helper builds |

### Android APK

```bash
./gradlew :androidApp:assembleDebug
# → androidApp/build/outputs/apk/debug/app-debug.apk
```

For a signed release APK, provide a keystore via environment variables and run:

```bash
ANDROID_KEYSTORE_PATH=/path/to/release.keystore \
ANDROID_KEYSTORE_PASSWORD=... \
ANDROID_KEY_ALIAS=... \
ANDROID_KEY_PASSWORD=... \
ANDROID_VERSION_NAME=1.2.3 \
ANDROID_VERSION_CODE=123 \
./gradlew :androidApp:assembleRelease
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

### sing-box engine

The sing-box engine is not bundled in source control. Android expects a generated mobile binding:

```bash
make sing-box-android
# writes:
#   sing-box-build/output/android/SingBoxCore.aar
#   androidApp/libs/SingBoxCore.aar
```

Desktop expects a bundled helper or a `sing-box` binary discoverable through `ULTRA_SING_BOX_PATH`, `~/.ultra-client/bin/sing-box`, or `PATH`. For local host binaries:

```bash
make sing-box-desktop
```

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

### Repository hygiene

Commit source code, Gradle files, GitHub workflows, SQLDelight schemas, app resources, launcher icons, `gradle-wrapper.jar`, and placeholder `.gitkeep` files for generated framework/library directories.

Do not commit local SDK config, IDE state, Gradle/Kotlin caches, build outputs, APK/AAB artifacts, sing-box build outputs, generated AAR/native binaries, downloaded tools, logs, environment files, or signing/provisioning files. Release binaries are produced by CI and attached to GitHub Releases.

---

## CI

### On every push to `main` / pull request

`.github/workflows/ci.yml` runs:
- **lint** — ktlint check on all Kotlin sources
- **test** — `jvmTest` for `shared:domain` and `shared:data`; HTML reports uploaded as artifacts

### On `v*.*.*` tag push

`.github/workflows/release.yml` runs tests, then:
- **android** — builds the Android APK and expects the sing-box mobile artifact to be supplied by CI/build setup. If Android signing secrets are configured, it uploads a signed release APK; otherwise it uploads a debug APK fallback.
- **ios** — builds XCFramework, then either a simulator build (default) or a signed IPA if `IOS_CERT_BASE64`, `IOS_CERT_PASSWORD`, `IOS_PROVISIONING_PROFILE_BASE64` secrets are set; uploads `ios-ipa-<tag>`

Android release signing secrets:

- `ANDROID_KEYSTORE_BASE64` — base64-encoded `.keystore` / `.jks`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

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
| Network engine | sing-box | latest |
| Android Gradle Plugin | AGP | 8.7.3 |
| Lint | ktlint | 1.5.0 |

---

## License

See [LICENSE](LICENSE).

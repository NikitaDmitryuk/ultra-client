# ultra-client: Complete KMP VPN Application — Implementation Plan

## Table of Contents

1. Version Matrix
2. Complete Gradle Project Structure
3. Full File Tree with Purpose Descriptions
4. All Interfaces, Classes, and Public API Signatures
5. Data Flow Architecture (Text Diagrams)
6. Android-Specific: VpnService Lifecycle, TUN Setup, JNI Bridge
7. iOS-Specific: NetworkExtension Setup, XCFramework Integration, Memory Constraints
8. expect/actual Pattern Catalogue
9. SQLDelight Schema (.sq files)
10. Xray JSON Config Generation Logic
11. VLESS URL Parsing Logic
12. Anti-Detection Module Architecture
13. Build Scripts for Xray-core via gomobile / libXray
14. ProGuard/R8 Rules
15. Step-by-Step Implementation Roadmap (7 Phases)

---

## 1. Version Matrix

```toml
# gradle/libs.versions.toml

[versions]
kotlin              = "2.1.21"        # Latest stable compatible with CMP 1.8+
agp                 = "8.7.3"         # Last AGP before 9.x (avoids KMP plugin migration)
compose             = "1.8.2"         # Stable iOS support
koin                = "4.1.1"
sqldelight          = "2.2.1"
voyager             = "1.1.0-beta03"
coroutines          = "1.10.2"
ktor                = "3.1.3"         # HTTP client for future use
serialization       = "1.8.1"
minSdk              = "26"            # Android 8 — gomobile min
targetSdk           = "35"
compileSdk          = "35"
jvmTarget           = "17"
```

Rationale: AGP 8.7.3 is chosen over 9.x because it still supports the classic `com.android.application` + KMP combination, avoiding the mandatory migration to `com.android.kotlin.multiplatform.library`. This simplifies the initial setup. The plan notes where to migrate later.

---

## 2. Complete Gradle Project Structure

### 2.1 Root `settings.gradle.kts`

Location: `/settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // For Voyager beta artifacts if needed
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
}

rootProject.name = "ultra-client"

include(":androidApp")
include(":iosApp")            // Not a Gradle module — Xcode project, excluded from build
include(":shared")
include(":shared:domain")
include(":shared:data")
include(":shared:presentation")

// iosApp is an Xcode project; exclude it from Gradle
// The shared KMP framework is consumed via XCFramework in Xcode
```

Note: `:shared:domain`, `:shared:data`, and `:shared:presentation` are Gradle sub-modules inside `:shared`. This keeps a clean dependency graph where `:data` depends on `:domain` and `:presentation` depends on `:domain`. `:androidApp` depends on all three.

### 2.2 Root `build.gradle.kts`

Location: `/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)    apply false
    alias(libs.plugins.kotlin.android)          apply false
    alias(libs.plugins.kotlin.serialization)    apply false
    alias(libs.plugins.android.application)     apply false
    alias(libs.plugins.android.library)         apply false
    alias(libs.plugins.sqldelight)              apply false
    alias(libs.plugins.compose.multiplatform)   apply false
    alias(libs.plugins.compose.compiler)        apply false
}
```

### 2.3 `gradle/libs.versions.toml` (Complete)

Location: `/gradle/libs.versions.toml`

```toml
[versions]
kotlin              = "2.1.21"
agp                 = "8.7.3"
compose             = "1.8.2"
koin                = "4.1.1"
sqldelight          = "2.2.1"
voyager             = "1.1.0-beta03"
coroutines          = "1.10.2"
ktor                = "3.1.3"
serialization       = "1.8.1"
lifecycle           = "2.9.0"

[libraries]
# Kotlin
kotlin-stdlib                       = { module = "org.jetbrains.kotlin:kotlin-stdlib",               version.ref = "kotlin" }
kotlin-test                         = { module = "org.jetbrains.kotlin:kotlin-test",                  version.ref = "kotlin" }
coroutines-core                     = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core",     version.ref = "coroutines" }
coroutines-android                  = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android",  version.ref = "coroutines" }
serialization-json                  = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json",  version.ref = "serialization" }

# Compose Multiplatform
compose-runtime                     = { module = "org.jetbrains.compose.runtime:runtime",                    version.ref = "compose" }
compose-ui                          = { module = "org.jetbrains.compose.ui:ui",                               version.ref = "compose" }
compose-material3                   = { module = "org.jetbrains.compose.material3:material3",                 version.ref = "compose" }
compose-foundation                  = { module = "org.jetbrains.compose.foundation:foundation",               version.ref = "compose" }
compose-components-resources        = { module = "org.jetbrains.compose.components:components-resources",     version.ref = "compose" }

# Koin
koin-core                           = { module = "io.insert-koin:koin-core",          version.ref = "koin" }
koin-android                        = { module = "io.insert-koin:koin-android",        version.ref = "koin" }
koin-compose                        = { module = "io.insert-koin:koin-compose",        version.ref = "koin" }
koin-compose-viewmodel              = { module = "io.insert-koin:koin-compose-viewmodel", version.ref = "koin" }

# SQLDelight
sqldelight-runtime                  = { module = "app.cash.sqldelight:runtime",               version.ref = "sqldelight" }
sqldelight-coroutines               = { module = "app.cash.sqldelight:coroutines-extensions",  version.ref = "sqldelight" }
sqldelight-android-driver           = { module = "app.cash.sqldelight:android-driver",         version.ref = "sqldelight" }
sqldelight-native-driver            = { module = "app.cash.sqldelight:native-driver",          version.ref = "sqldelight" }

# Voyager
voyager-navigator                   = { module = "cafe.adriel.voyager:voyager-navigator",   version.ref = "voyager" }
voyager-screenmodel                 = { module = "cafe.adriel.voyager:voyager-screenmodel", version.ref = "voyager" }
voyager-transitions                 = { module = "cafe.adriel.voyager:voyager-transitions", version.ref = "voyager" }
voyager-koin                        = { module = "cafe.adriel.voyager:voyager-koin",        version.ref = "voyager" }

# Android
android-lifecycle-runtime           = { module = "androidx.lifecycle:lifecycle-runtime-ktx",      version.ref = "lifecycle" }
android-activity-compose            = { module = "androidx.activity:activity-compose",            version = "1.10.1" }

[plugins]
kotlin-multiplatform                = { id = "org.jetbrains.kotlin.multiplatform",          version.ref = "kotlin" }
kotlin-android                      = { id = "org.jetbrains.kotlin.android",                version.ref = "kotlin" }
kotlin-serialization                = { id = "org.jetbrains.kotlin.plugin.serialization",   version.ref = "kotlin" }
android-application                 = { id = "com.android.application",                     version.ref = "agp" }
android-library                     = { id = "com.android.library",                         version.ref = "agp" }
compose-multiplatform               = { id = "org.jetbrains.compose",                       version.ref = "compose" }
compose-compiler                    = { id = "org.jetbrains.kotlin.plugin.compose",         version.ref = "kotlin" }
sqldelight                          = { id = "app.cash.sqldelight",                         version.ref = "sqldelight" }
```

### 2.4 Module `shared/domain/build.gradle.kts`

Location: `/shared/domain/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = "17" }
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
```

### 2.5 Module `shared/data/build.gradle.kts`

Location: `/shared/data/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.nikdmitryuk.ultraclient.data"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = "17" }
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:domain"))
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.coroutines.android)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

sqldelight {
    databases {
        create("UltraClientDatabase") {
            packageName.set("io.nikdmitryuk.ultraclient.data.local.db")
        }
    }
}
```

### 2.6 Module `shared/presentation/build.gradle.kts`

Location: `/shared/presentation/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "io.nikdmitryuk.ultraclient.presentation"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = "17" }
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:domain"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.foundation)
            implementation(libs.compose.components.resources)
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.screenmodel)
            implementation(libs.voyager.transitions)
            implementation(libs.voyager.koin)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }

    // iOS XCFramework for Xcode consumption
    val xcfName = "SharedPresentation"
    iosX64().binaries.framework { baseName = xcfName; isStatic = true }
    iosArm64().binaries.framework { baseName = xcfName; isStatic = true }
    iosSimulatorArm64().binaries.framework { baseName = xcfName; isStatic = true }
}
```

### 2.7 Module `androidApp/build.gradle.kts`

Location: `/androidApp/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "io.nikdmitryuk.ultraclient.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.nikdmitryuk.ultraclient"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
        }
        jniLibs.pickFirsts += "**/*.so"
    }
}

dependencies {
    implementation(project(":shared:domain"))
    implementation(project(":shared:data"))
    implementation(project(":shared:presentation"))
    implementation(libs.android.lifecycle.runtime)
    implementation(libs.android.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.coroutines.android)

    // XrayCore AAR — placed in androidApp/libs/
    implementation(files("libs/XrayCore.aar"))
}
```

---

## 3. Full File Tree with Every .kt File Listed

```
/ultra-client/
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties    # Gradle 8.11
├── build.gradle.kts
├── settings.gradle.kts
├── LICENSE
├── README.md
│
├── androidApp/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   ├── libs/
│   │   └── XrayCore.aar                 # Pre-compiled Xray Android AAR (generated by build script)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── kotlin/io/nikdmitryuk/ultraclient/android/
│       │   ├── MainActivity.kt          # Single-activity host; starts Compose, initializes Koin
│       │   ├── di/
│       │   │   └── AndroidAppModule.kt  # androidApp Koin module: provides Context, VpnServiceBinder
│       │   └── vpn/
│       │       ├── UltraVpnService.kt   # extends VpnService; TUN lifecycle; foreground service
│       │       ├── TunConfigurator.kt   # VpnService.Builder configuration; routes/DNS setup
│       │       ├── XrayBridge.kt        # JNI/Java bridge to XrayCore.aar; startXray/stopXray
│       │       ├── KillSwitchManager.kt # Enforces traffic block via VpnService with empty routes
│       │       └── SplitTunnelHelper.kt # Calls addDisallowedApplication() per config
│       └── res/
│           ├── mipmap-*/                # Launcher icons
│           └── xml/
│               └── network_security_config.xml
│
├── shared/
│   ├── domain/
│   │   ├── build.gradle.kts
│   │   └── src/commonMain/kotlin/io/nikdmitryuk/ultraclient/domain/
│   │       ├── model/
│   │       │   ├── VpnProfile.kt        # Data class: id, name, rawUrl, parsedConfig, isFavorite
│   │       │   ├── VlessConfig.kt       # Data class: all VLESS fields (uuid, host, port, flow…)
│   │       │   ├── VpnState.kt          # Sealed class: Disconnected, Connecting, Connected, Error
│   │       │   ├── SplitTunnelRule.kt   # Data class: packageName/bundleId, isExcluded
│   │       │   └── AntiDetectConfig.kt  # Data class: killSwitch, fakeDns, randomPort, splitTunnel
│   │       ├── repository/
│   │       │   ├── VpnProfileRepository.kt  # interface: CRUD for VpnProfile
│   │       │   └── AntiDetectRepository.kt  # interface: persist/load AntiDetectConfig
│   │       └── usecase/
│   │           ├── ConnectVpnUseCase.kt          # orchestrates: parse→generate config→start engine
│   │           ├── DisconnectVpnUseCase.kt        # stops engine; updates state
│   │           ├── ImportProfileUseCase.kt        # parses raw VLESS URL; stores profile
│   │           ├── GetVpnStateUseCase.kt          # returns Flow<VpnState>
│   │           ├── GetProfilesUseCase.kt          # returns Flow<List<VpnProfile>>
│   │           ├── DeleteProfileUseCase.kt        # deletes by id
│   │           ├── SetActiveProfileUseCase.kt     # marks one profile active
│   │           ├── UpdateSplitTunnelUseCase.kt    # persist SplitTunnelRule changes
│   │           └── UpdateAntiDetectUseCase.kt     # persist AntiDetectConfig changes
│   │
│   ├── data/
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── commonMain/kotlin/io/nikdmitryuk/ultraclient/data/
│   │       │   ├── di/
│   │       │   │   └── DataModule.kt            # Koin module wiring for data layer
│   │       │   ├── local/
│   │       │   │   ├── DatabaseDriverFactory.kt  # expect class: provides SqlDriver
│   │       │   │   ├── VpnProfileLocalDataSource.kt  # SQLDelight queries wrapper
│   │       │   │   └── AntiDetectLocalDataSource.kt  # SQLDelight queries wrapper
│   │       │   ├── repository/
│   │       │   │   ├── VpnProfileRepositoryImpl.kt   # implements domain interface
│   │       │   │   └── AntiDetectRepositoryImpl.kt   # implements domain interface
│   │       │   ├── vpn/
│   │       │   │   ├── VpnEngine.kt              # expect class: platform VPN lifecycle
│   │       │   │   ├── VpnStateHolder.kt          # StateFlow<VpnState> shared across layers
│   │       │   │   ├── XrayConfigBuilder.kt        # Generates Xray JSON from VlessConfig
│   │       │   │   └── VlessUrlParser.kt           # Parses vless:// URI into VlessConfig
│   │       │   └── antidetect/
│   │       │       ├── PortRandomizer.kt           # generates random unused local port (>1024)
│   │       │       └── FakeDnsConfigurator.kt       # injects fakedns block into Xray JSON
│   │       ├── androidMain/kotlin/io/nikdmitryuk/ultraclient/data/
│   │       │   ├── local/
│   │       │   │   └── DatabaseDriverFactory.android.kt  # actual: AndroidSqliteDriver
│   │       │   └── vpn/
│   │       │       └── VpnEngine.android.kt              # actual: binds to UltraVpnService
│   │       └── iosMain/kotlin/io/nikdmitryuk/ultraclient/data/
│   │           ├── local/
│   │           │   └── DatabaseDriverFactory.ios.kt      # actual: NativeSqliteDriver
│   │           └── vpn/
│   │               └── VpnEngine.ios.kt                  # actual: NETunnelProviderManager bridge
│   │
│   └── presentation/
│       ├── build.gradle.kts
│       └── src/
│           ├── commonMain/kotlin/io/nikdmitryuk/ultraclient/presentation/
│           │   ├── App.kt                   # Root @Composable; Navigator(HomeScreen())
│           │   ├── di/
│           │   │   └── PresentationModule.kt  # Koin module: all ScreenModels
│           │   ├── theme/
│           │   │   ├── Theme.kt             # MaterialTheme wrapper
│           │   │   ├── Color.kt             # Color palette
│           │   │   └── Type.kt              # Typography
│           │   └── screen/
│           │       ├── home/
│           │       │   ├── HomeScreen.kt        # Voyager Screen: VPN toggle, status, profile button
│           │       │   └── HomeScreenModel.kt   # StateFlow<HomeUiState>; connect/disconnect actions
│           │       ├── profiles/
│           │       │   ├── ProfilesScreen.kt    # Voyager Screen: list of VpnProfiles; paste button
│           │       │   └── ProfilesScreenModel.kt  # list of profiles; import from clipboard
│           │       ├── settings/
│           │       │   ├── SettingsScreen.kt      # Voyager Screen: anti-detect toggles
│           │       │   └── SettingsScreenModel.kt # AntiDetectConfig state; update use cases
│           │       └── components/
│           │           ├── VpnStatusIndicator.kt  # Composable: animated dot + text label
│           │           ├── PowerButton.kt         # Composable: large toggle button
│           │           ├── ProfileCard.kt         # Composable: single profile row
│           │           └── SplitTunnelItem.kt     # Composable: per-app toggle row
│           ├── androidMain/kotlin/io/nikdmitryuk/ultraclient/presentation/
│           │   └── MainViewController.android.kt  # (empty; Android uses MainActivity directly)
│           └── iosMain/kotlin/io/nikdmitryuk/ultraclient/presentation/
│               └── MainViewController.ios.kt      # fun MainViewController(): UIViewController
│
├── iosApp/
│   ├── iosApp.xcodeproj/
│   │   └── project.pbxproj              # Xcode project file
│   ├── iosApp/
│   │   ├── iOSApp.swift                 # @main SwiftUI entry; init Koin; present MainViewController
│   │   ├── ContentView.swift            # UIViewControllerRepresentable wrapping MainViewController
│   │   └── Info.plist
│   ├── NetworkExtension/                # Separate Xcode target: PacketTunnelProvider
│   │   ├── PacketTunnelProvider.swift   # NEPacketTunnelProvider subclass; calls LibXray
│   │   └── Info.plist
│   └── Frameworks/
│       └── LibXray.xcframework          # Pre-compiled Xray iOS XCFramework
│
└── xray-build/
    ├── build.sh                         # Master build script for Xray AAR + XCFramework
    ├── build-android.sh                 # gomobile bind → XrayCore.aar
    ├── build-ios.sh                     # gomobile bind → LibXray.xcframework
    └── go.mod                           # Go module wrapping libXray
```

---

## 4. All Interfaces, Classes, and Public API Signatures

### 4.1 Domain Layer — Models

**`VpnProfile.kt`** — `commonMain`
```kotlin
package io.nikdmitryuk.ultraclient.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class VpnProfile(
    val id: String,           // UUID generated at import time
    val name: String,         // Human-readable label (from fragment of VLESS URL)
    val rawUrl: String,       // Original vless:// string
    val config: VlessConfig,  // Parsed structured config
    val isActive: Boolean = false,
    val createdAt: Long       // epoch millis
)
```

**`VlessConfig.kt`** — `commonMain`
```kotlin
package io.nikdmitryuk.ultraclient.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class VlessConfig(
    val uuid: String,
    val address: String,
    val port: Int,
    val encryption: String = "none",
    val flow: String = "",            // "xtls-rprx-vision" or ""
    val security: String,             // "reality", "tls", "none"
    val network: String = "tcp",      // "tcp", "ws", "grpc"
    // Reality-specific
    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val realitySpiderX: String = "",
    val sni: String = "",
    val fingerprint: String = "chrome",
    // TLS-specific
    val alpn: String = "",
    // WebSocket-specific
    val wsPath: String = "",
    val wsHost: String = "",
    // gRPC-specific
    val grpcServiceName: String = ""
)
```

**`VpnState.kt`** — `commonMain`
```kotlin
package io.nikdmitryuk.ultraclient.domain.model

sealed class VpnState {
    object Disconnected : VpnState()
    object Connecting   : VpnState()
    data class Connected(val serverAddress: String, val connectedAt: Long) : VpnState()
    data class Error(val message: String) : VpnState()
}
```

**`SplitTunnelRule.kt`** — `commonMain`
```kotlin
package io.nikdmitryuk.ultraclient.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SplitTunnelRule(
    val appId: String,      // Android: packageName / iOS: bundleId
    val appName: String,    // Display name for UI
    val isExcluded: Boolean // true = bypass VPN
)
```

**`AntiDetectConfig.kt`** — `commonMain`
```kotlin
package io.nikdmitryuk.ultraclient.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AntiDetectConfig(
    val killSwitchEnabled: Boolean = false,
    val fakeDnsEnabled: Boolean = true,
    val randomPortEnabled: Boolean = true,
    val splitTunnelRules: List<SplitTunnelRule> = emptyList()
)
```

### 4.2 Domain Layer — Repositories (interfaces)

**`VpnProfileRepository.kt`** — `commonMain`
```kotlin
package io.nikdmitryuk.ultraclient.domain.repository

import io.nikdmitryuk.ultraclient.domain.model.VpnProfile
import kotlinx.coroutines.flow.Flow

interface VpnProfileRepository {
    fun observeAll(): Flow<List<VpnProfile>>
    suspend fun getById(id: String): VpnProfile?
    suspend fun getActive(): VpnProfile?
    suspend fun insert(profile: VpnProfile)
    suspend fun setActive(id: String)
    suspend fun delete(id: String)
    suspend fun deleteAll()
}
```

**`AntiDetectRepository.kt`** — `commonMain`
```kotlin
package io.nikdmitryuk.ultraclient.domain.repository

import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import kotlinx.coroutines.flow.Flow

interface AntiDetectRepository {
    fun observe(): Flow<AntiDetectConfig>
    suspend fun get(): AntiDetectConfig
    suspend fun update(config: AntiDetectConfig)
}
```

### 4.3 Domain Layer — Use Cases

**`ConnectVpnUseCase.kt`** — `commonMain`
```kotlin
package io.nikdmitryuk.ultraclient.domain.usecase

import io.nikdmitryuk.ultraclient.domain.model.VpnProfile
import io.nikdmitryuk.ultraclient.domain.repository.AntiDetectRepository
import io.nikdmitryuk.ultraclient.data.vpn.VpnEngine

class ConnectVpnUseCase(
    private val profileRepository: VpnProfileRepository,
    private val antiDetectRepository: AntiDetectRepository,
    private val vpnEngine: VpnEngine
) {
    suspend operator fun invoke(profileId: String): Result<Unit>
}
```

**`DisconnectVpnUseCase.kt`** — `commonMain`
```kotlin
class DisconnectVpnUseCase(private val vpnEngine: VpnEngine) {
    suspend operator fun invoke(): Result<Unit>
}
```

**`ImportProfileUseCase.kt`** — `commonMain`
```kotlin
class ImportProfileUseCase(
    private val parser: VlessUrlParser,
    private val repository: VpnProfileRepository
) {
    // Returns the newly created VpnProfile or throws ParseException
    suspend operator fun invoke(rawUrl: String): Result<VpnProfile>
}
```

**`GetVpnStateUseCase.kt`** — `commonMain`
```kotlin
class GetVpnStateUseCase(private val vpnEngine: VpnEngine) {
    operator fun invoke(): Flow<VpnState>
}
```

**`GetProfilesUseCase.kt`** — `commonMain`
```kotlin
class GetProfilesUseCase(private val repository: VpnProfileRepository) {
    operator fun invoke(): Flow<List<VpnProfile>>
}
```

**`DeleteProfileUseCase.kt`** — `commonMain`
```kotlin
class DeleteProfileUseCase(private val repository: VpnProfileRepository) {
    suspend operator fun invoke(id: String): Result<Unit>
}
```

**`SetActiveProfileUseCase.kt`** — `commonMain`
```kotlin
class SetActiveProfileUseCase(private val repository: VpnProfileRepository) {
    suspend operator fun invoke(id: String): Result<Unit>
}
```

**`UpdateSplitTunnelUseCase.kt`** — `commonMain`
```kotlin
class UpdateSplitTunnelUseCase(private val repository: AntiDetectRepository) {
    suspend operator fun invoke(rules: List<SplitTunnelRule>): Result<Unit>
}
```

**`UpdateAntiDetectUseCase.kt`** — `commonMain`
```kotlin
class UpdateAntiDetectUseCase(private val repository: AntiDetectRepository) {
    suspend operator fun invoke(config: AntiDetectConfig): Result<Unit>
}
```

### 4.4 Data Layer

**`VlessUrlParser.kt`** — `commonMain`
```kotlin
package io.nikdmitryuk.ultraclient.data.vpn

import io.nikdmitryuk.ultraclient.domain.model.VlessConfig

class VlessUrlParser {
    // Throws VlessParseException with descriptive message on failure
    fun parse(rawUrl: String): VlessConfig
    
    // Internal helpers:
    private fun decodeFragment(encoded: String): String
    private fun parseQueryParams(query: String): Map<String, String>
    private fun requireParam(params: Map<String, String>, key: String): String
}

class VlessParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

**`XrayConfigBuilder.kt`** — `commonMain`
```kotlin
package io.nikdmitryuk.ultraclient.data.vpn

import io.nikdmitryuk.ultraclient.domain.model.VlessConfig
import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig

class XrayConfigBuilder {
    // Returns a complete Xray JSON config string
    fun build(
        vlessConfig: VlessConfig,
        antiDetectConfig: AntiDetectConfig,
        localSocksPort: Int,
        localDnsPort: Int
    ): String

    private fun buildInboundsJson(socksPort: Int): String
    private fun buildOutboundJson(cfg: VlessConfig): String
    private fun buildRealityStreamSettings(cfg: VlessConfig): String
    private fun buildTlsStreamSettings(cfg: VlessConfig): String
    private fun buildDnsJson(antiDetect: AntiDetectConfig, dnsPort: Int): String
    private fun buildRoutingJson(antiDetect: AntiDetectConfig): String
    private fun buildFakeDnsJson(): String
}
```

**`VpnStateHolder.kt`** — `commonMain`
```kotlin
package io.nikdmitryuk.ultraclient.data.vpn

import io.nikdmitryuk.ultraclient.domain.model.VpnState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Singleton state bus shared between data layer and platform VPN service
object VpnStateHolder {
    private val _state = MutableStateFlow<VpnState>(VpnState.Disconnected)
    val state: StateFlow<VpnState> = _state

    fun emit(newState: VpnState) { _state.value = newState }
}
```

**`VpnEngine.kt`** (expect class) — `commonMain`
```kotlin
package io.nikdmitryuk.ultraclient.data.vpn

import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import io.nikdmitryuk.ultraclient.domain.model.VlessConfig
import io.nikdmitryuk.ultraclient.domain.model.VpnState
import kotlinx.coroutines.flow.Flow

expect class VpnEngine {
    val state: Flow<VpnState>
    suspend fun connect(config: VlessConfig, antiDetect: AntiDetectConfig): Result<Unit>
    suspend fun disconnect(): Result<Unit>
    fun isConnected(): Boolean
}
```

**`DatabaseDriverFactory.kt`** (expect class) — `commonMain`
```kotlin
package io.nikdmitryuk.ultraclient.data.local

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
```

**`PortRandomizer.kt`** — `commonMain`
```kotlin
package io.nikdmitryuk.ultraclient.data.antidetect

class PortRandomizer {
    // Returns a pseudo-random port in range [10000, 60000]
    // Regenerated each time connect() is called
    fun randomSocksPort(): Int
    fun randomDnsPort(): Int
    
    private fun isPortAvailable(port: Int): Boolean
}
```

**`FakeDnsConfigurator.kt`** — `commonMain`
```kotlin
package io.nikdmitryuk.ultraclient.data.antidetect

class FakeDnsConfigurator {
    // Injects fakedns object into the Xray JSON map
    fun inject(configMap: MutableMap<String, Any>, dnsPort: Int): MutableMap<String, Any>
}
```

**`VpnProfileLocalDataSource.kt`** — `commonMain`
```kotlin
package io.nikdmitryuk.ultraclient.data.local

import io.nikdmitryuk.ultraclient.data.local.db.UltraClientDatabase
import io.nikdmitryuk.ultraclient.domain.model.VpnProfile
import kotlinx.coroutines.flow.Flow

class VpnProfileLocalDataSource(private val db: UltraClientDatabase) {
    fun observeAll(): Flow<List<VpnProfile>>
    suspend fun getById(id: String): VpnProfile?
    suspend fun getActive(): VpnProfile?
    suspend fun insert(profile: VpnProfile)
    suspend fun setActive(id: String)
    suspend fun delete(id: String)
}
```

**`AntiDetectLocalDataSource.kt`** — `commonMain`
```kotlin
package io.nikdmitryuk.ultraclient.data.local

import io.nikdmitryuk.ultraclient.domain.model.AntiDetectConfig
import kotlinx.coroutines.flow.Flow

class AntiDetectLocalDataSource(private val db: UltraClientDatabase) {
    fun observe(): Flow<AntiDetectConfig>
    suspend fun get(): AntiDetectConfig
    suspend fun upsert(config: AntiDetectConfig)
}
```

### 4.5 Data Layer — Android Actuals

**`VpnEngine.android.kt`** — `androidMain`
```kotlin
actual class VpnEngine(private val context: android.content.Context) {
    actual val state: Flow<VpnState> get() = VpnStateHolder.state
    actual suspend fun connect(config: VlessConfig, antiDetect: AntiDetectConfig): Result<Unit>
    actual suspend fun disconnect(): Result<Unit>
    actual fun isConnected(): Boolean
    
    // Sends Intent to UltraVpnService with serialized config
    private fun startVpnService(jsonConfig: String, antiDetect: AntiDetectConfig)
    private fun stopVpnService()
}
```

**`DatabaseDriverFactory.android.kt`** — `androidMain`
```kotlin
actual class DatabaseDriverFactory(private val context: android.content.Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(UltraClientDatabase.Schema, context, "ultra_client.db")
}
```

### 4.6 Data Layer — iOS Actuals

**`VpnEngine.ios.kt`** — `iosMain`
```kotlin
import platform.NetworkExtension.NETunnelProviderManager
import platform.NetworkExtension.NEVPNStatus

actual class VpnEngine {
    actual val state: Flow<VpnState>
    actual suspend fun connect(config: VlessConfig, antiDetect: AntiDetectConfig): Result<Unit>
    actual suspend fun disconnect(): Result<Unit>
    actual fun isConnected(): Boolean
    
    // Manages NETunnelProviderManager lifecycle
    private var manager: NETunnelProviderManager? = null
    private fun observeVpnStatus()
    private suspend fun loadOrCreateManager(): NETunnelProviderManager
    private fun buildProviderProtocol(jsonConfig: String): NETunnelProviderProtocol
}
```

**`DatabaseDriverFactory.ios.kt`** — `iosMain`
```kotlin
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(UltraClientDatabase.Schema, "ultra_client.db")
}
```

### 4.7 Presentation Layer

**`HomeUiState.kt`** (data class inside HomeScreenModel) — `commonMain`
```kotlin
data class HomeUiState(
    val vpnState: VpnState = VpnState.Disconnected,
    val activeProfile: VpnProfile? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
```

**`HomeScreenModel.kt`** — `commonMain`
```kotlin
class HomeScreenModel(
    private val connectVpnUseCase: ConnectVpnUseCase,
    private val disconnectVpnUseCase: DisconnectVpnUseCase,
    private val getVpnStateUseCase: GetVpnStateUseCase,
    private val getProfilesUseCase: GetProfilesUseCase
) : ScreenModel {
    val uiState: StateFlow<HomeUiState>

    fun toggleVpn()               // connects if Disconnected, disconnects otherwise
    fun onPermissionResult(granted: Boolean)  // Android: VPN permission result
    
    override fun onDispose()
}
```

**`ProfilesUiState.kt`** (data class inside ProfilesScreenModel)
```kotlin
data class ProfilesUiState(
    val profiles: List<VpnProfile> = emptyList(),
    val isImporting: Boolean = false,
    val importError: String? = null
)
```

**`ProfilesScreenModel.kt`** — `commonMain`
```kotlin
class ProfilesScreenModel(
    private val getProfilesUseCase: GetProfilesUseCase,
    private val importProfileUseCase: ImportProfileUseCase,
    private val deleteProfileUseCase: DeleteProfileUseCase,
    private val setActiveProfileUseCase: SetActiveProfileUseCase,
    private val clipboardReader: ClipboardReader   // expect interface
) : ScreenModel {
    val uiState: StateFlow<ProfilesUiState>

    fun importFromClipboard()
    fun deleteProfile(id: String)
    fun setActiveProfile(id: String)
    fun clearError()
}
```

**`ClipboardReader.kt`** (expect interface) — `commonMain`
```kotlin
package io.nikdmitryuk.ultraclient.presentation

expect interface ClipboardReader {
    fun readText(): String?
}
```

**`SettingsUiState.kt`** (data class inside SettingsScreenModel)
```kotlin
data class SettingsUiState(
    val config: AntiDetectConfig = AntiDetectConfig(),
    val availableApps: List<SplitTunnelRule> = emptyList()
)
```

**`SettingsScreenModel.kt`** — `commonMain`
```kotlin
class SettingsScreenModel(
    private val antiDetectRepository: AntiDetectRepository,
    private val updateAntiDetectUseCase: UpdateAntiDetectUseCase,
    private val updateSplitTunnelUseCase: UpdateSplitTunnelUseCase,
    private val installedAppsProvider: InstalledAppsProvider  // expect interface
) : ScreenModel {
    val uiState: StateFlow<SettingsUiState>

    fun toggleKillSwitch(enabled: Boolean)
    fun toggleFakeDns(enabled: Boolean)
    fun toggleRandomPort(enabled: Boolean)
    fun toggleAppExclusion(appId: String, excluded: Boolean)
}
```

**`InstalledAppsProvider.kt`** (expect interface) — `commonMain`
```kotlin
expect interface InstalledAppsProvider {
    suspend fun getInstalledApps(): List<SplitTunnelRule>
}
```

### 4.8 Android-Specific Classes

**`UltraVpnService.kt`** — `androidApp`
```kotlin
package io.nikdmitryuk.ultraclient.android.vpn

import android.net.VpnService

class UltraVpnService : VpnService() {
    companion object {
        const val ACTION_CONNECT = "io.nikdmitryuk.ultraclient.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "io.nikdmitryuk.ultraclient.ACTION_DISCONNECT"
        const val EXTRA_XRAY_CONFIG = "xray_config_json"
        const val EXTRA_ANTI_DETECT = "anti_detect_json"
    }

    override fun onCreate()
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
    override fun onRevoke()        // Kill switch activation point
    override fun onDestroy()

    private fun startTunnel(xrayConfigJson: String, antiDetectJson: String)
    private fun stopTunnel()
    private fun buildNotification(): Notification
    private fun startForeground()
}
```

**`TunConfigurator.kt`** — `androidApp`
```kotlin
package io.nikdmitryuk.ultraclient.android.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor

class TunConfigurator(private val service: VpnService) {
    // Builds and establishes TUN; returns ParcelFileDescriptor
    fun establish(
        localSocksPort: Int,
        antiDetectConfig: AntiDetectConfig
    ): ParcelFileDescriptor

    private fun applyRoutes(builder: VpnService.Builder)
    private fun applyDns(builder: VpnService.Builder, fakeDns: Boolean)
    private fun applySplitTunnel(
        builder: VpnService.Builder,
        rules: List<SplitTunnelRule>
    )
}
```

**`XrayBridge.kt`** — `androidApp`
```kotlin
package io.nikdmitryuk.ultraclient.android.vpn

// Wraps the Java API exposed by XrayCore.aar (libXray gomobile output)
object XrayBridge {
    // Called after TUN is established; Xray reads from the file descriptor
    fun startXray(configJson: String, tunFd: Int): Boolean
    fun stopXray(): Boolean
    fun isRunning(): Boolean
    fun getStats(): String  // JSON stats string from Xray
    
    // libXray exposes these as Java methods from gomobile:
    // io.xtls.libxray.Libxray.startXray(config: String): Boolean
    // io.xtls.libxray.Libxray.stopXray(): Boolean
}
```

**`KillSwitchManager.kt`** — `androidApp`
```kotlin
package io.nikdmitryuk.ultraclient.android.vpn

import android.content.Context

class KillSwitchManager(private val context: Context) {
    // Starts a minimal VpnService that routes all traffic to void (no server)
    // Only used if VpnService.Builder.setBlocking(true) is not sufficient
    fun activate()
    fun deactivate()
    fun isActive(): Boolean
}
```

**`SplitTunnelHelper.kt`** — `androidApp`
```kotlin
package io.nikdmitryuk.ultraclient.android.vpn

import android.net.VpnService

object SplitTunnelHelper {
    fun apply(
        builder: VpnService.Builder,
        rules: List<SplitTunnelRule>,
        context: android.content.Context
    )
}
```

### 4.9 Koin DI Modules

**`DataModule.kt`** — `commonMain` in shared/data
```kotlin
val dataModule = module {
    // DatabaseDriverFactory is provided by platformDataModule
    single { UltraClientDatabase(get<DatabaseDriverFactory>().createDriver()) }
    single { VpnProfileLocalDataSource(get()) }
    single { AntiDetectLocalDataSource(get()) }
    single<VpnProfileRepository> { VpnProfileRepositoryImpl(get()) }
    single<AntiDetectRepository> { AntiDetectRepositoryImpl(get()) }
    single { VlessUrlParser() }
    single { XrayConfigBuilder() }
    single { PortRandomizer() }
    single { FakeDnsConfigurator() }
    // VpnEngine is provided by platformDataModule
}

// Each platform provides:
expect val platformDataModule: Module
```

**`DataModule.android.kt`** — `androidMain`
```kotlin
actual val platformDataModule: Module = module {
    single { DatabaseDriverFactory(get()) }   // get() = Context
    single { VpnEngine(get()) }               // get() = Context
}
```

**`DataModule.ios.kt`** — `iosMain`
```kotlin
actual val platformDataModule: Module = module {
    single { DatabaseDriverFactory() }
    single { VpnEngine() }
}
```

**`PresentationModule.kt`** — `commonMain` in shared/presentation
```kotlin
val presentationModule = module {
    factory { HomeScreenModel(get(), get(), get(), get()) }
    factory { ProfilesScreenModel(get(), get(), get(), get(), get()) }
    factory { SettingsScreenModel(get(), get(), get(), get()) }
}
```

**`AndroidAppModule.kt`** — `androidApp`
```kotlin
val androidAppModule = module {
    single<ClipboardReader> { AndroidClipboardReader(get()) }
    single<InstalledAppsProvider> { AndroidInstalledAppsProvider(get()) }
}
```

---

## 5. Data Flow Architecture (Text Diagrams)

### 5.1 VPN Connection Flow

```
User taps "Connect" on HomeScreen
         │
         ▼
HomeScreenModel.toggleVpn()
         │
         ▼
[Android only] VpnService.prepare() Intent launched
         │ (permission granted callback)
         ▼
ConnectVpnUseCase.invoke(profileId)
         │
         ├─► VpnProfileRepository.getById(profileId)  ──► SQLDelight DB
         │         returns VpnProfile
         │
         ├─► AntiDetectRepository.get()  ──────────────► SQLDelight DB
         │         returns AntiDetectConfig
         │
         ├─► PortRandomizer.randomSocksPort()  ──────►  random Int (e.g. 18492)
         │
         ├─► XrayConfigBuilder.build(vlessConfig, antiDetect, socksPort, dnsPort)
         │         returns JSON String
         │
         └─► VpnEngine.connect(config, antiDetect)
                   │
                   ▼ (Android actual)
              Sends Intent ACTION_CONNECT to UltraVpnService
                   │
                   ▼
              UltraVpnService.onStartCommand()
                   │
                   ├─► TunConfigurator.establish()
                   │     - VpnService.Builder.addAddress("10.0.0.1", 32)
                   │     - .addRoute("0.0.0.0", 0)
                   │     - .addDnsServer("198.18.0.3")  [fake DNS IP]
                   │     - .setMtu(1500)
                   │     - .setSession("ultra-client")
                   │     - SplitTunnelHelper.apply() → addDisallowedApplication()
                   │     - .establish()  → ParcelFileDescriptor (tunFd)
                   │
                   ├─► XrayBridge.startXray(configJson, tunFd.fd)
                   │     - libXray.Libxray.startXray(config)
                   │     - Xray binds to socks://127.0.0.1:{randomPort}
                   │     - Xray reads TUN packets via fd
                   │
                   └─► VpnStateHolder.emit(VpnState.Connected(...))
                             │
                             ▼ (StateFlow)
                        HomeScreenModel.uiState updated
                             │
                             ▼
                        HomeScreen recomposes → shows "Connected"
```

### 5.2 VLESS Profile Import Flow

```
User taps "Paste" on ProfilesScreen
         │
         ▼
ProfilesScreenModel.importFromClipboard()
         │
         ▼
ClipboardReader.readText()  ──► platform clipboard
         │  returns "vless://uuid@host:443?..."
         ▼
ImportProfileUseCase.invoke(rawUrl)
         │
         ▼
VlessUrlParser.parse(rawUrl)
         │  Validates scheme, parses URI components
         │  Returns VlessConfig or throws VlessParseException
         ▼
VpnProfile(id=UUID.random(), name=fragment, config=parsedConfig, rawUrl=rawUrl)
         │
         ▼
VpnProfileRepository.insert(profile)
         │
         ▼
VpnProfileLocalDataSource.insert(profile)
         │
         ▼
SQLDelight → INSERT INTO vpn_profiles(...)
         │
         ▼
observeAll() Flow emits → ProfilesScreen recomposes
```

### 5.3 Anti-Detection State Machine

```
AntiDetectConfig persisted in DB
         │
         ├── killSwitchEnabled=true →
         │        On VpnState.Error or onRevoke():
         │        UltraVpnService re-establishes TUN with no server (drop everything)
         │        or calls builder.setBlocking(true) before establishing
         │
         ├── fakeDnsEnabled=true →
         │        XrayConfigBuilder injects "fakedns" into dns block
         │        and adds FakeDNS inbound on port {dnsPort}
         │        TunConfigurator sets DNS server to "198.18.0.3" (FakeDNS IP)
         │
         ├── randomPortEnabled=true →
         │        PortRandomizer.randomSocksPort() called per connect
         │        New random port used in Xray socks inbound each session
         │
         └── splitTunnelRules non-empty →
                  SplitTunnelHelper.apply() loops rules
                  Calls builder.addDisallowedApplication(appId) for isExcluded=true
```

---

## 6. Android-Specific: VpnService Lifecycle, TUN Setup, JNI Bridge

### 6.1 AndroidManifest.xml (critical entries)

```xml
<!-- androidApp/src/main/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <application
        android:name=".UltraClientApplication"
        android:label="ultra-client"
        android:networkSecurityConfig="@xml/network_security_config">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- VPN Service: requires BIND_VPN_SERVICE permission -->
        <service
            android:name=".vpn.UltraVpnService"
            android:exported="false"
            android:foregroundServiceType="specialUse"
            android:permission="android.permission.BIND_VPN_SERVICE">
            <intent-filter>
                <action android:name="android.net.VpnService" />
            </intent-filter>
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="vpn" />
        </service>

    </application>
</manifest>
```

### 6.2 UltraVpnService Lifecycle Details

The service lifecycle follows these steps in order:

1. `onCreate()` — acquire WakeLock; initialize Xray logging directory
2. `onStartCommand(ACTION_CONNECT)` — deserialize config from intent extras; call `startTunnel()`
3. `startTunnel()`:
   - Call `TunConfigurator.establish()` → get `ParcelFileDescriptor` (tunFd)
   - Call `startForeground(NOTIFICATION_ID, buildNotification())` with ongoing notification
   - Call `XrayBridge.startXray(xrayConfigJson, tunFd.fd)` passing the TUN file descriptor integer
   - Register a watchdog coroutine: poll `XrayBridge.isRunning()` every 5 seconds; on false, emit `VpnState.Error` and optionally activate Kill Switch
4. `onRevoke()` — called by Android when user revokes permission externally:
   - `XrayBridge.stopXray()`
   - `tunFd.close()`
   - `VpnStateHolder.emit(VpnState.Disconnected)`
   - If `killSwitchEnabled`: re-establish TUN with no routes/server to block traffic
5. `onStartCommand(ACTION_DISCONNECT)` — stop tunnel gracefully
6. `onDestroy()` — release WakeLock; close TUN fd; stop foreground

### 6.3 TUN Address Allocation

Xray on Android does not need a real TUN packet loop in Kotlin — gomobile-compiled Xray takes the TUN file descriptor directly via `startXray(config, fd)` and handles all packet I/O internally in Go goroutines. The Kotlin side only needs to:
- Create the TUN via `VpnService.Builder.establish()`
- Pass the resulting `fd` integer to Xray
- Keep the `ParcelFileDescriptor` reference alive (do not close until disconnect)

TUN address: `10.0.0.1/32`
Routes: `0.0.0.0/0` (all IPv4) + `::/0` (all IPv6, optional)
MTU: 1500
DNS: `198.18.0.3` when fakeDns enabled, else `1.1.1.1`

### 6.4 XrayCore AAR API (from libXray)

The gomobile-compiled AAR exposes the following Java class (auto-generated by gomobile from Go exported symbols):

```java
// Auto-generated by gomobile — do not edit
package io.xtls.libxray;

public final class Libxray {
    // Start Xray with JSON config. fd is the TUN file descriptor integer.
    // Returns error string or empty string on success.
    public static String startXray(String configPath);
    
    // For fd-based approach, libXray may expose:
    public static String startXrayWithFd(String config, long fd);
    
    public static String stopXray();
    public static boolean isXrayRunning();
    public static String queryStats(String tag, String direct);
}
```

Note: The exact method signatures depend on the version of libXray. Check the actual generated Java stubs in `XrayCore.aar/classes.jar` after building. The `XrayBridge.kt` wrapper must match those stubs exactly.

### 6.5 Kill Switch Implementation

Android's native Kill Switch is implemented by re-establishing the TUN interface with blocking mode when `onRevoke()` fires or when the Xray watchdog detects a crash:

```kotlin
// Inside UltraVpnService
private fun activateKillSwitch() {
    val builder = Builder()
    builder.setSession("ultra-client-killswitch")
    builder.addAddress("10.0.0.2", 32)
    // Do NOT add routes — all traffic is implicitly blocked
    // because the TUN has no routes and no server to forward to
    // setBlocking(true) makes reads on the fd block until a packet arrives
    builder.setBlocking(true)
    killSwitchFd = builder.establish()
    VpnStateHolder.emit(VpnState.Error("Kill switch active"))
}
```

The kill switch TUN absorbs all traffic but never forwards it, effectively cutting internet while keeping the VPN tunnel "up" per Android's rules (only one VPN service allowed at a time).

---

## 7. iOS-Specific: NetworkExtension Setup, XCFramework Integration, Memory Constraints

### 7.1 Project Architecture on iOS

There are two separate Xcode targets:

1. **Main App Target (`iosApp`)**: Contains the SwiftUI entry point, imports `SharedPresentation.xcframework` (the KMP Compose Multiplatform UI), and manages the `NETunnelProviderManager` lifecycle from the UI side.

2. **Network Extension Target (`NetworkExtension`)**: A separate app extension bundle. Contains `PacketTunnelProvider.swift` which extends `NEPacketTunnelProvider`. This extension is where `LibXray.xcframework` is actually imported and the VPN tunnel runs.

### 7.2 Memory Constraint Strategy

The iOS Network Extension has a hard memory limit (historically ~50MB on recent devices but enforced by jetsam at ~15MB on older iOS). The KMP Kotlin/Native runtime itself is about 4–7 MB. `LibXray.xcframework` adds further overhead. To stay within limits:

- The `NetworkExtension` target does NOT import any KMP framework. It is pure Swift + LibXray.
- All configuration (the Xray JSON config string) is passed from the main app to the extension via `NETunnelProviderSession.sendProviderMessage(_:responseHandler:)`.
- The KMP `VpnEngine.ios.kt` builds the config JSON in the main process and sends it to the extension as a `Data` message.
- The extension holds only: LibXray runtime + Swift stdlib + Foundation.

### 7.3 Info.plist for Network Extension

```xml
<!-- iosApp/NetworkExtension/Info.plist -->
<key>NSExtension</key>
<dict>
    <key>NSExtensionPointIdentifier</key>
    <string>com.apple.networkextension.packet-tunnel</string>
    <key>NSExtensionPrincipalClass</key>
    <string>$(PRODUCT_MODULE_NAME).PacketTunnelProvider</string>
</dict>
```

### 7.4 PacketTunnelProvider.swift

```swift
// iosApp/NetworkExtension/PacketTunnelProvider.swift
import NetworkExtension
import LibXray  // imported from LibXray.xcframework

class PacketTunnelProvider: NEPacketTunnelProvider {

    private var xrayStarted = false

    override func startTunnel(
        options: [String: NSObject]?,
        completionHandler: @escaping (Error?) -> Void
    ) {
        // 1. Receive config JSON from main app (sent via sendProviderMessage)
        // The config is passed in options["config"] as a Data/String
        guard let configData = options?["config"] as? String else {
            completionHandler(NSError(domain: "ultra", code: 1))
            return
        }

        // 2. Write config to extension's cache directory (Xray reads from file path)
        let configPath = writeConfigToFile(configData)

        // 3. Set tunnel network settings
        let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: "127.0.0.1")
        settings.iPv4Settings = NEIPv4Settings(addresses: ["10.0.0.1"], subnetMasks: ["255.255.255.0"])
        settings.iPv4Settings?.includedRoutes = [NEIPv4Route.default()]
        settings.dnsSettings = NEDNSSettings(servers: ["198.18.0.3"])
        settings.mtu = 1500

        setTunnelNetworkSettings(settings) { [weak self] error in
            guard let self = self, error == nil else {
                completionHandler(error)
                return
            }

            // 4. Start Xray with the TUN file descriptor
            // packetFlow.value(forKeyPath: "socket.fileDescriptor") is a private API alternative
            // The standard approach: Xray reads from packetFlow directly via LibXray bridge
            let err = LibxrayStartXray(configPath)
            if err.isEmpty {
                self.xrayStarted = true
                completionHandler(nil)
            } else {
                completionHandler(NSError(domain: "xray", code: 2, userInfo: [NSLocalizedDescriptionKey: err]))
            }
        }
    }

    override func stopTunnel(
        with reason: NEProviderStopReason,
        completionHandler: @escaping () -> Void
    ) {
        if xrayStarted {
            LibxrayStopXray()
            xrayStarted = false
        }
        completionHandler()
    }

    override func handleAppMessage(
        _ messageData: Data,
        completionHandler: ((Data?) -> Void)?
    ) {
        // Handle runtime messages from main app (e.g., update config)
        let response = Data("ok".utf8)
        completionHandler?(response)
    }

    private func writeConfigToFile(_ config: String) -> String {
        let dir = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: "group.io.nikdmitryuk.ultraclient"
        )!
        let path = dir.appendingPathComponent("xray_config.json").path
        try? config.write(toFile: path, atomically: true, encoding: .utf8)
        return path
    }
}
```

### 7.5 VpnEngine.ios.kt — NETunnelProviderManager

```kotlin
// shared/data/src/iosMain/kotlin/io/nikdmitryuk/ultraclient/data/vpn/VpnEngine.ios.kt
import platform.NetworkExtension.*
import platform.Foundation.*
import kotlinx.coroutines.flow.*

actual class VpnEngine {
    private val _state = MutableStateFlow<VpnState>(VpnState.Disconnected)
    actual val state: Flow<VpnState> = _state

    actual suspend fun connect(
        config: VlessConfig,
        antiDetect: AntiDetectConfig
    ): Result<Unit> = runCatching {
        val configJson = XrayConfigBuilder().build(
            config, antiDetect,
            PortRandomizer().randomSocksPort(),
            PortRandomizer().randomDnsPort()
        )
        val manager = loadOrCreateManager()
        val session = manager.connection as NETunnelProviderSession
        // Pass config as start options
        val options = mapOf("config" to configJson)
        session.startTunnelWithOptions(options) { error ->
            if (error != null) {
                _state.value = VpnState.Error(error.localizedDescription)
            }
        }
    }

    actual suspend fun disconnect(): Result<Unit> = runCatching {
        val manager = loadOrCreateManager()
        val session = manager.connection as NETunnelProviderSession
        session.stopTunnel()
    }

    actual fun isConnected(): Boolean =
        _state.value is VpnState.Connected

    private suspend fun loadOrCreateManager(): NETunnelProviderManager {
        // NETunnelProviderManager.loadAllFromPreferences using Kotlin/Native coroutine bridge
        val managers = NETunnelProviderManager.loadAllFromPreferencesWithCompletionHandler { _, _ -> }
        // Returns first or creates new
        return managers?.firstOrNull() as? NETunnelProviderManager ?: createNewManager()
    }

    private fun createNewManager(): NETunnelProviderManager {
        val manager = NETunnelProviderManager()
        val proto = NETunnelProviderProtocol()
        proto.providerBundleIdentifier = "io.nikdmitryuk.ultraclient.NetworkExtension"
        proto.serverAddress = "ultra-client"
        manager.protocolConfiguration = proto
        manager.localizedDescription = "ultra-client"
        manager.isEnabled = true
        manager.saveToPreferencesWithCompletionHandler { _ -> }
        return manager
    }
}
```

### 7.6 Required Entitlements

**Main App entitlements** (`iosApp.entitlements`):
```xml
<key>com.apple.developer.networking.networkextension</key>
<array>
    <string>packet-tunnel-provider</string>
</array>
<key>com.apple.security.application-groups</key>
<array>
    <string>group.io.nikdmitryuk.ultraclient</string>
</array>
```

**Network Extension entitlements** (`NetworkExtension.entitlements`):
```xml
<key>com.apple.developer.networking.networkextension</key>
<array>
    <string>packet-tunnel-provider</string>
</array>
<key>com.apple.security.application-groups</key>
<array>
    <string>group.io.nikdmitryuk.ultraclient</string>
</array>
```

The App Group shared container is used to pass the Xray config file between main app and extension without IPC overhead.

### 7.7 iOSApp.swift Entry Point

```swift
// iosApp/iosApp/iOSApp.swift
import SwiftUI
import SharedPresentation  // KMP Compose Multiplatform XCFramework
import shared              // KMP domain/data XCFramework

@main
struct iOSApp: App {
    init() {
        // Initialize Koin for iOS
        KoinInitKt.doInitKoin(modules: [])
    }
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

// ContentView.swift
struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        return MainViewController()  // KMP function from iosMain
    }
    func updateUIViewController(_ vc: UIViewController, context: Context) {}
}
```

---

## 8. expect/actual Pattern Catalogue

All expect/actual declarations in the project:

| expect declaration | androidMain actual | iosMain actual | Purpose |
|---|---|---|---|
| `expect class VpnEngine` | Uses `VpnService` Intent | Uses `NETunnelProviderManager` | Platform VPN lifecycle |
| `expect class DatabaseDriverFactory` | `AndroidSqliteDriver` | `NativeSqliteDriver` | SQLDelight driver |
| `expect val platformDataModule: Module` | Android Koin bindings | iOS Koin bindings | Platform DI wiring |
| `expect interface ClipboardReader` | `ClipboardManager` API | `UIPasteboard.general` | Read clipboard text |
| `expect interface InstalledAppsProvider` | `PackageManager.getInstalledPackages()` | `LSApplicationQueriesSchemes` | App list for split tunnel |

### `ClipboardReader` Android actual:

```kotlin
// androidMain
class AndroidClipboardReader(private val context: Context) : ClipboardReader {
    override fun readText(): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.getItemAt(0)?.text?.toString()
    }
}
```

### `ClipboardReader` iOS actual:

```kotlin
// iosMain
import platform.UIKit.UIPasteboard
class IosClipboardReader : ClipboardReader {
    override fun readText(): String? = UIPasteboard.generalPasteboard.string
}
```

### `InstalledAppsProvider` Android actual:

```kotlin
// androidMain
class AndroidInstalledAppsProvider(private val context: Context) : InstalledAppsProvider {
    override suspend fun getInstalledApps(): List<SplitTunnelRule> {
        return context.packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .map { SplitTunnelRule(appId = it.packageName, appName = it.loadLabel(context.packageManager).toString(), isExcluded = false) }
    }
}
```

### `InstalledAppsProvider` iOS actual:

```kotlin
// iosMain
// iOS does not expose installed app lists to other apps; return empty
class IosInstalledAppsProvider : InstalledAppsProvider {
    override suspend fun getInstalledApps(): List<SplitTunnelRule> = emptyList()
}
```

Note: Split tunneling on iOS via `NEPacketTunnelProvider` is not configurable per-app by the VPN app itself (iOS routes all traffic through the tunnel by default). The split tunnel feature is therefore Android-only in the MVP. The iOS UI should hide the split tunnel section.

---

## 9. SQLDelight Schema (.sq files)

### File: `shared/data/src/commonMain/sqldelight/io/nikdmitryuk/ultraclient/data/local/db/VpnProfiles.sq`

```sql
CREATE TABLE vpn_profiles (
    id           TEXT    NOT NULL PRIMARY KEY,
    name         TEXT    NOT NULL,
    raw_url      TEXT    NOT NULL,
    config_json  TEXT    NOT NULL,   -- serialized VlessConfig as JSON
    is_active    INTEGER NOT NULL DEFAULT 0,  -- 0=false, 1=true; only one row can be 1
    created_at   INTEGER NOT NULL
);

-- Queries
selectAll:
SELECT * FROM vpn_profiles ORDER BY created_at DESC;

selectById:
SELECT * FROM vpn_profiles WHERE id = :id;

selectActive:
SELECT * FROM vpn_profiles WHERE is_active = 1 LIMIT 1;

insert:
INSERT OR REPLACE INTO vpn_profiles(id, name, raw_url, config_json, is_active, created_at)
VALUES (:id, :name, :raw_url, :config_json, :is_active, :created_at);

setActiveById:
UPDATE vpn_profiles SET is_active = CASE WHEN id = :id THEN 1 ELSE 0 END;

deleteById:
DELETE FROM vpn_profiles WHERE id = :id;

deleteAll:
DELETE FROM vpn_profiles;

countProfiles:
SELECT COUNT(*) FROM vpn_profiles;
```

### File: `shared/data/src/commonMain/sqldelight/io/nikdmitryuk/ultraclient/data/local/db/AntiDetect.sq`

```sql
CREATE TABLE anti_detect_config (
    id                   INTEGER NOT NULL PRIMARY KEY DEFAULT 1,  -- singleton row
    kill_switch_enabled  INTEGER NOT NULL DEFAULT 0,
    fake_dns_enabled     INTEGER NOT NULL DEFAULT 1,
    random_port_enabled  INTEGER NOT NULL DEFAULT 1,
    split_tunnel_json    TEXT    NOT NULL DEFAULT '[]'  -- serialized List<SplitTunnelRule>
);

-- Queries
select:
SELECT * FROM anti_detect_config WHERE id = 1;

upsert:
INSERT OR REPLACE INTO anti_detect_config(
    id, kill_switch_enabled, fake_dns_enabled, random_port_enabled, split_tunnel_json
) VALUES (1, :kill_switch_enabled, :fake_dns_enabled, :random_port_enabled, :split_tunnel_json);
```

### Migration files

`shared/data/src/commonMain/sqldelight/migrations/1.sqm` — initial schema (no changes, just documents v1):
```sql
-- Version 1: initial schema
-- vpn_profiles and anti_detect_config tables created
```

Future migrations go in `2.sqm`, `3.sqm`, etc.

---

## 10. Xray JSON Config Generation Logic

### Full `XrayConfigBuilder.build()` Output

The builder produces a JSON string conforming to Xray's configuration format. The generated JSON for a VLESS+Reality connection with FakeDNS enabled and random port 18492:

```json
{
  "log": {
    "loglevel": "warning",
    "access": "",
    "error": ""
  },
  "dns": {
    "servers": [
      {
        "address": "fakedns",
        "domains": ["geosite:geolocation-!cn"]
      },
      "1.1.1.1"
    ],
    "fakedns": {
      "ipPool": "198.18.0.0/15",
      "poolSize": 65535
    }
  },
  "inbounds": [
    {
      "tag": "socks-in",
      "listen": "127.0.0.1",
      "port": 18492,
      "protocol": "socks",
      "settings": {
        "auth": "noauth",
        "udp": true,
        "ip": "127.0.0.1"
      },
      "sniffing": {
        "enabled": true,
        "destOverride": ["http", "tls", "fakedns"]
      }
    },
    {
      "tag": "dns-in",
      "listen": "198.18.0.3",
      "port": 53,
      "protocol": "dokodemo-door",
      "settings": {
        "address": "1.1.1.1",
        "port": 53,
        "network": "udp"
      }
    }
  ],
  "outbounds": [
    {
      "tag": "proxy-out",
      "protocol": "vless",
      "settings": {
        "vnext": [
          {
            "address": "example.com",
            "port": 443,
            "users": [
              {
                "id": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
                "encryption": "none",
                "flow": "xtls-rprx-vision"
              }
            ]
          }
        ]
      },
      "streamSettings": {
        "network": "tcp",
        "security": "reality",
        "realitySettings": {
          "fingerprint": "chrome",
          "serverName": "www.example.com",
          "publicKey": "BASE64_PUBLIC_KEY",
          "shortId": "SHORTID",
          "spiderX": "/"
        }
      }
    },
    {
      "tag": "direct",
      "protocol": "freedom",
      "settings": {}
    },
    {
      "tag": "block",
      "protocol": "blackhole",
      "settings": {}
    },
    {
      "tag": "dns-out",
      "protocol": "dns"
    }
  ],
  "routing": {
    "domainStrategy": "IPIfNonMatch",
    "rules": [
      {
        "type": "field",
        "inboundTag": ["dns-in"],
        "outboundTag": "dns-out"
      },
      {
        "type": "field",
        "ip": ["geoip:private"],
        "outboundTag": "direct"
      },
      {
        "type": "field",
        "domain": ["geosite:category-ads-all"],
        "outboundTag": "block"
      },
      {
        "type": "field",
        "network": "tcp,udp",
        "outboundTag": "proxy-out"
      }
    ]
  }
}
```

### Config Builder Pseudocode

```kotlin
fun build(vlessConfig: VlessConfig, antiDetect: AntiDetectConfig, socksPort: Int, dnsPort: Int): String {
    val root = mutableMapOf<String, Any>()
    
    root["log"] = buildLog()
    root["dns"] = if (antiDetect.fakeDnsEnabled) buildFakeDns() else buildPlainDns()
    root["inbounds"] = buildInbounds(socksPort, antiDetect.fakeDnsEnabled)
    root["outbounds"] = buildOutbounds(vlessConfig)
    root["routing"] = buildRouting(antiDetect.fakeDnsEnabled)
    
    return Json.encodeToString(root)  // or use kotlinx.serialization manually
}
```

---

## 11. VLESS URL Parsing Logic

### URL Format Reference

```
vless://{uuid}@{host}:{port}?{queryParams}#{fragment}

Required params: encryption
Optional params: flow, security, sni, fp, pbk, sid, spx, type, path, host, serviceName, alpn
```

### `VlessUrlParser.parse()` Implementation Details

```kotlin
fun parse(rawUrl: String): VlessConfig {
    // 1. Validate scheme
    if (!rawUrl.startsWith("vless://")) throw VlessParseException("Not a vless:// URL")

    // 2. Strip "vless://"
    val withoutScheme = rawUrl.removePrefix("vless://")

    // 3. Split at "@" to get userInfo and rest
    val atIndex = withoutScheme.indexOf('@')
    if (atIndex < 0) throw VlessParseException("Missing @ separator")
    val uuid = withoutScheme.substring(0, atIndex)
    val rest = withoutScheme.substring(atIndex + 1)

    // 4. Split fragment (#)
    val hashIndex = rest.indexOf('#')
    val fragmentEncoded = if (hashIndex >= 0) rest.substring(hashIndex + 1) else ""
    val label = decodeFragment(fragmentEncoded)
    val withoutFragment = if (hashIndex >= 0) rest.substring(0, hashIndex) else rest

    // 5. Split query (?)
    val qIndex = withoutFragment.indexOf('?')
    val hostPort = if (qIndex >= 0) withoutFragment.substring(0, qIndex) else withoutFragment
    val query = if (qIndex >= 0) withoutFragment.substring(qIndex + 1) else ""

    // 6. Parse host:port
    val colonIdx = hostPort.lastIndexOf(':')
    if (colonIdx < 0) throw VlessParseException("Missing port")
    val host = hostPort.substring(0, colonIdx)
    val port = hostPort.substring(colonIdx + 1).toIntOrNull()
        ?: throw VlessParseException("Invalid port")

    // 7. Parse query parameters
    val params = parseQueryParams(query)
    val security = params["security"] ?: "none"
    val network = params["type"] ?: "tcp"

    return VlessConfig(
        uuid = uuid,
        address = host,
        port = port,
        encryption = params["encryption"] ?: "none",
        flow = params["flow"] ?: "",
        security = security,
        network = network,
        sni = params["sni"] ?: "",
        fingerprint = params["fp"] ?: "chrome",
        realityPublicKey = params["pbk"] ?: "",
        realityShortId = params["sid"] ?: "",
        realitySpiderX = params["spx"]?.let { decodeFragment(it) } ?: "/",
        alpn = params["alpn"] ?: "",
        wsPath = params["path"]?.let { decodeFragment(it) } ?: "",
        wsHost = params["host"] ?: "",
        grpcServiceName = params["serviceName"] ?: ""
    )
}

private fun parseQueryParams(query: String): Map<String, String> {
    if (query.isEmpty()) return emptyMap()
    return query.split("&")
        .filter { it.contains("=") }
        .associate { param ->
            val eq = param.indexOf('=')
            param.substring(0, eq) to param.substring(eq + 1)
        }
}

private fun decodeFragment(encoded: String): String {
    // URL percent-decode; use platform-agnostic implementation
    return encoded
        .replace("+", " ")
        .replace(Regex("%([0-9A-Fa-f]{2})")) { mr ->
            mr.groupValues[1].toInt(16).toChar().toString()
        }
}
```

---

## 12. Anti-Detection Module Architecture

### 12.1 Component Overview

```
AntiDetectConfig (domain model)
       │
       ├── Kill Switch
       │     Implementation: KillSwitchManager (Android)
       │     Trigger: VpnState.Error OR onRevoke()
       │     Mechanism: Re-establish TUN with no routes; blocks all traffic
       │     iOS: NEPacketTunnelProvider auto-blocks when stopped (iOS default)
       │
       ├── Fake DNS / DNS-over-Tunnel
       │     Implementation: FakeDnsConfigurator (commonMain)
       │     Mechanism: Xray "fakedns" in dns block; dokodemo-door inbound on port 53
       │     TUN DNS: 198.18.0.3 (non-routable IP intercepted by Xray)
       │     Benefit: All DNS queries go through Xray tunnel → no DNS leaks
       │
       ├── Random Local Ports
       │     Implementation: PortRandomizer (commonMain)
       │     Mechanism: New random socks port + dns port per connect() call
       │     Range: 10000–60000 (avoids well-known ports)
       │     Benefit: Port scanners cannot build fingerprints of client ports
       │
       ├── Split Tunneling (per-app exclusion)
       │     Implementation: SplitTunnelHelper (Android) / InstalledAppsProvider stub (iOS)
       │     Mechanism: VpnService.Builder.addDisallowedApplication(packageName)
       │     Benefit: Excluded apps bypass VPN entirely
       │     iOS: Not available in Network Extension (system limitation)
       │
       └── VLESS Reality (TLS fingerprint masking)
             Implementation: XrayConfigBuilder.buildRealityStreamSettings()
             Mechanism: uTLS fingerprint in realitySettings.fingerprint
             Values: "chrome", "firefox", "safari", "ios", "android", "edge", "random"
             Benefit: Traffic is indistinguishable from real Chrome/Firefox HTTPS
```

### 12.2 FakeDNS Injection Logic

When `AntiDetectConfig.fakeDnsEnabled = true`, `XrayConfigBuilder` injects:

```
dns.servers → prepend { "address": "fakedns", "domains": ["geosite:geolocation-!cn"] }
dns.fakedns → { "ipPool": "198.18.0.0/15", "poolSize": 65535 }
inbounds    → add dokodemo-door on 198.18.0.3:53 (catches TUN DNS queries)
routing     → add rule: inboundTag dns-in → outboundTag dns-out
outbounds   → add { "tag": "dns-out", "protocol": "dns" }
sniffing    → add "fakedns" to destOverride in socks inbound
```

### 12.3 Kill Switch Activation Sequence (Android)

```
Scenario: Xray process dies unexpectedly
                │
Watchdog detects XrayBridge.isRunning() == false
                │
                ▼
VpnStateHolder.emit(VpnState.Error("Xray crashed"))
                │
                ├── killSwitchEnabled == false →
                │         stopSelf(); TUN fd closed; traffic flows normally
                │
                └── killSwitchEnabled == true →
                          KillSwitchManager.activate()
                          New TUN with address only, no routes
                          All packets absorbed but never forwarded
                          Internet is blocked until user manually reconnects
```

---

## 13. Build Scripts for Xray-core via gomobile / libXray

### 13.1 Directory Structure for Go Code

```
/xray-build/
├── build.sh            # Master script: calls both android and ios builds
├── build-android.sh    # Android AAR build
├── build-ios.sh        # iOS XCFramework build
└── go.mod              # Go module file
```

### 13.2 `xray-build/go.mod`

```go
module io.nikdmitryuk.ultraclient/xray-build

go 1.23

require (
    github.com/xtls/libxray v0.0.0-latest
)

// Required: libXray and Xray-core must be siblings
replace github.com/xtls/xray-core => ../xray-core-src/Xray-core
replace github.com/xtls/libxray => ../xray-core-src/libXray
```

Note: At build time, clone both repos into `xray-core-src/` before running the build scripts.

### 13.3 `xray-build/build.sh` (Master Script)

```bash
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
SRC_DIR="$SCRIPT_DIR/xray-core-src"

echo "=== Cloning / updating Xray-core sources ==="
mkdir -p "$SRC_DIR"

if [ ! -d "$SRC_DIR/Xray-core" ]; then
    git clone --depth 1 https://github.com/XTLS/Xray-core.git "$SRC_DIR/Xray-core"
else
    git -C "$SRC_DIR/Xray-core" pull
fi

if [ ! -d "$SRC_DIR/libXray" ]; then
    git clone --depth 1 https://github.com/XTLS/libXray.git "$SRC_DIR/libXray"
else
    git -C "$SRC_DIR/libXray" pull
fi

echo "=== Building Android AAR ==="
bash "$SCRIPT_DIR/build-android.sh"

echo "=== Building iOS XCFramework ==="
bash "$SCRIPT_DIR/build-ios.sh"

echo "=== Copying artifacts ==="
cp "$SCRIPT_DIR/output/android/XrayCore.aar" "$PROJECT_ROOT/androidApp/libs/XrayCore.aar"
cp -R "$SCRIPT_DIR/output/ios/LibXray.xcframework" "$PROJECT_ROOT/iosApp/Frameworks/LibXray.xcframework"

echo "=== Done ==="
```

### 13.4 `xray-build/build-android.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$SCRIPT_DIR/xray-core-src"
OUTPUT_DIR="$SCRIPT_DIR/output/android"

# Requires: Go 1.23+, Android NDK, ANDROID_HOME set
# Requires: gomobile installed or will be installed from go.mod

mkdir -p "$OUTPUT_DIR"

cd "$SRC_DIR/libXray"

# Install gomobile using the version pinned in go.mod
GOMOBILE_VERSION=$(awk '/golang.org\/x\/mobile/ {print $2}' go.mod 2>/dev/null || echo "latest")
echo "Installing gomobile @ $GOMOBILE_VERSION"
go install golang.org/x/mobile/cmd/gomobile@"$GOMOBILE_VERSION"

# Initialize gomobile Android NDK
gomobile init

# Build for all Android ABIs
for ABI in arm arm64 386 amd64; do
    echo "Building Android $ABI..."
    gomobile bind \
        -o "$OUTPUT_DIR/XrayCore-$ABI.aar" \
        -target "android/$ABI" \
        -androidapi 26 \
        -ldflags="-buildid= -s -w" \
        -trimpath \
        github.com/xtls/libxray
done

# Merge AARs into a single fat AAR (or use the arm64 one as primary)
# Simplest approach: use the arm64 AAR and rename
cp "$OUTPUT_DIR/XrayCore-arm64.aar" "$OUTPUT_DIR/XrayCore.aar"

echo "Android AAR built at: $OUTPUT_DIR/XrayCore.aar"
```

### 13.5 `xray-build/build-ios.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

# Requires: macOS with Xcode installed, Go 1.23+
# Requires: iOS Simulator Runtime installed

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$SCRIPT_DIR/xray-core-src"
OUTPUT_DIR="$SCRIPT_DIR/output/ios"

mkdir -p "$OUTPUT_DIR"

cd "$SRC_DIR/libXray"

# Use libXray's own build script (Python 3 required)
# Method: gomobile (recommended for KMP interop)
python3 build/main.py apple gomobile

# libXray outputs to: output/apple/gomobile/
cp -R output/apple/gomobile/LibXray.xcframework "$OUTPUT_DIR/LibXray.xcframework"

echo "iOS XCFramework built at: $OUTPUT_DIR/LibXray.xcframework"
```

Alternative if the Python script is unavailable:

```bash
#!/usr/bin/env bash
# Direct gomobile approach
gomobile init

gomobile bind \
    -o "$OUTPUT_DIR/LibXray.xcframework" \
    -target "ios,iossimulator" \
    -iosversion 16.0 \
    -ldflags="-s -w" \
    -trimpath \
    github.com/xtls/libxray

echo "iOS XCFramework built at: $OUTPUT_DIR/LibXray.xcframework"
```

### 13.6 Prerequisites Checklist for Developers

```
Android build:
  □ Go 1.23+ installed (go.dev/dl)
  □ Android NDK r27+ installed via SDK Manager
  □ ANDROID_HOME env var set
  □ ANDROID_NDK_HOME env var set
  □ Java 17+

iOS build (macOS only):
  □ Xcode 16+ installed from App Store
  □ iOS Simulator Runtime installed (for iossimulator target)
  □ Go 1.23+ installed
  □ Python 3.9+ installed (for libXray build script)
  □ gomobile: go install golang.org/x/mobile/cmd/gomobile@latest
  □ gomobile init (downloads NDK toolchain for mobile targets)
```

---

## 14. ProGuard/R8 Rules

### `/androidApp/proguard-rules.pro`

```proguard
# ============================================================
# ultra-client ProGuard / R8 Rules
# ============================================================

# --- Kotlin ---
-keepattributes Signature, RuntimeVisibleAnnotations, AnnotationDefault, *Annotation*
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** { @kotlin.Metadata <fields>; }
-keepclassmembers class * extends java.lang.Enum {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-dontwarn kotlin.**
-dontwarn kotlinx.**

# --- Kotlin Coroutines ---
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# --- Kotlin Serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class ** {
    @kotlinx.serialization.Serializable <methods>;
}
-keep @kotlinx.serialization.Serializable class * { *; }

# --- Koin ---
-keep class io.insert-koin.** { *; }
-keep class org.koin.** { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.* *;
}
-dontwarn org.koin.**

# --- SQLDelight ---
-keep class app.cash.sqldelight.** { *; }
-keep class io.nikdmitryuk.ultraclient.data.local.db.** { *; }  # generated DB classes
-dontwarn app.cash.sqldelight.**

# --- Voyager ---
-keep class cafe.adriel.voyager.** { *; }
-dontwarn cafe.adriel.voyager.**

# --- Compose ---
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# --- XrayCore AAR (libXray gomobile output) ---
-keep class io.xtls.libxray.** { *; }
-keep class libxray.** { *; }                 # gomobile generated package name
-dontwarn io.xtls.libxray.**
-dontwarn libxray.**
# Ensure JNI-loaded native libs are not stripped
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- Android VPN Service ---
-keep class io.nikdmitryuk.ultraclient.android.vpn.UltraVpnService { *; }
-keep class io.nikdmitryuk.ultraclient.android.** { *; }

# --- Domain Models (passed via serialization, must not be obfuscated) ---
-keep class io.nikdmitryuk.ultraclient.domain.model.** { *; }

# --- Debug output: uncomment to trace keep rule sources ---
# -printconfiguration build/outputs/logs/proguard-config.txt
```

---

## 15. Step-by-Step Implementation Roadmap

### Phase 0: Repository Bootstrap (Day 1)

1. Create `gradle/libs.versions.toml` with the version matrix from Section 1.
2. Create root `settings.gradle.kts` registering `:androidApp`, `:shared:domain`, `:shared:data`, `:shared:presentation`.
3. Create root `build.gradle.kts` with plugin declarations (all `apply false`).
4. Create `gradle/wrapper/gradle-wrapper.properties` pointing to Gradle 8.11.
5. Create `shared/domain/build.gradle.kts` — KMP, no Android, no compose.
6. Create `shared/data/build.gradle.kts` — KMP + Android library + SQLDelight.
7. Create `shared/presentation/build.gradle.kts` — KMP + Android library + Compose.
8. Create `androidApp/build.gradle.kts` — Android application.
9. Create `androidApp/src/main/AndroidManifest.xml` from Section 6.1.
10. Verify the project syncs in Android Studio (no compilation yet).

Deliverable: Project syncs cleanly. No code, just build files.

### Phase 1: Domain Layer (Days 2–3)

1. Create all model files: `VpnProfile.kt`, `VlessConfig.kt`, `VpnState.kt`, `SplitTunnelRule.kt`, `AntiDetectConfig.kt`.
2. Create repository interfaces: `VpnProfileRepository.kt`, `AntiDetectRepository.kt`.
3. Create all use case classes with constructor signatures (no implementation bodies yet — just `TODO()`).
4. Write `commonTest` unit tests for model equality and serialization.

Deliverable: Domain module compiles. All models are `@Serializable`. Use cases have correct constructor signatures.

### Phase 2: Data Layer — Database (Days 4–5)

1. Create `.sq` schema files (`VpnProfiles.sq`, `AntiDetect.sq`) from Section 9.
2. Run `./gradlew generateSqlDelightInterface` to verify schema generates `UltraClientDatabase`.
3. Implement `DatabaseDriverFactory` expect class + both actuals.
4. Implement `VpnProfileLocalDataSource` — map between DB rows and domain models.
5. Implement `AntiDetectLocalDataSource` — singleton row pattern.
6. Implement `VpnProfileRepositoryImpl` and `AntiDetectRepositoryImpl`.
7. Wire `DataModule.kt` Koin module.

Deliverable: Database layer compiles. SQLDelight generates typesafe queries. Manual test: insert → observe on Android emulator.

### Phase 3: VLESS Parsing + Xray Config Generation (Days 6–7)

1. Implement `VlessUrlParser.parse()` fully from Section 11. Cover all query parameters.
2. Write `commonTest` unit tests for VLESS URL parsing:
   - Valid VLESS+Reality URL
   - Valid VLESS+TLS URL
   - Valid VLESS+WS URL
   - Missing UUID → `VlessParseException`
   - Invalid port → `VlessParseException`
3. Implement `PortRandomizer` — `kotlin.random.Random` for randomness (KMP-safe).
4. Implement `XrayConfigBuilder.build()` fully from Section 10. Output must be valid JSON.
5. Write unit test: build config → verify JSON keys present via `kotlinx.serialization`.
6. Implement `FakeDnsConfigurator.inject()`.
7. Implement all use cases (fill in `TODO()` bodies using injected deps).

Deliverable: VLESS parsing and config generation are fully tested. Use cases are implemented.

### Phase 4: VPN Engine — Android (Days 8–11)

1. Add `XrayCore.aar` placeholder (empty AAR or real one from build script) to `androidApp/libs/`.
2. Implement `XrayBridge.kt` wrapping the AAR Java API.
3. Implement `UltraVpnService.kt` — full lifecycle from Section 6.2.
4. Implement `TunConfigurator.kt` — Builder configuration from Section 6.3.
5. Implement `SplitTunnelHelper.kt`.
6. Implement `KillSwitchManager.kt`.
7. Implement `VpnEngine.android.kt` — sends intents to `UltraVpnService`.
8. Implement `VpnStateHolder` (already defined as `object`).
9. Implement `MainActivity.kt` — handles VPN permission intent result and Koin init.
10. Run `UltraClientApplication.kt` (entry point for Koin startKoin).

Test: On Android emulator (API 26+), toggle connect/disconnect manually. Verify `VpnState` transitions.

Deliverable: VPN connects on Android. Traffic routes through Xray. Status updates correctly.

### Phase 5: Compose Multiplatform UI (Days 12–15)

1. Implement `Theme.kt`, `Color.kt`, `Type.kt` (Material3 dark/light palettes).
2. Implement `HomeScreen.kt` + `HomeScreenModel.kt`:
   - `PowerButton` composable: large circular button, color changes with state
   - `VpnStatusIndicator` composable: animated pulsing dot
   - Active profile name display
3. Implement `ProfilesScreen.kt` + `ProfilesScreenModel.kt`:
   - List of profiles using `LazyColumn`
   - "Paste from clipboard" FAB
   - Swipe-to-delete using `SwipeToDismissBox`
4. Implement `SettingsScreen.kt` + `SettingsScreenModel.kt`:
   - Kill Switch toggle
   - Fake DNS toggle
   - Random Port toggle
   - Split Tunnel list (Android only; hidden on iOS using `expect/actual` or platform detection)
5. Implement `App.kt` with Voyager `Navigator(HomeScreen())` and `SlideTransition`.
6. Implement `PresentationModule.kt` Koin module.
7. Wire Koin initialization in `MainActivity.kt` and `iOSApp.swift`.

Deliverable: Full UI working on Android. Screens navigate correctly. State reflects VPN status.

### Phase 6: iOS Integration (Days 16–20)

1. Create Xcode project with two targets: `iosApp` and `NetworkExtension`.
2. Run `./gradlew :shared:presentation:assembleXCFramework` to generate `SharedPresentation.xcframework`.
3. Add the XCFramework to Xcode: **General → Frameworks, Libraries → Add Files**.
4. Configure App Group entitlements for both targets (Section 7.6).
5. Implement `PacketTunnelProvider.swift` from Section 7.4.
6. Add `LibXray.xcframework` to the `NetworkExtension` target only.
7. Implement `VpnEngine.ios.kt` from Section 7.5.
8. Implement `MainViewController.ios.kt` returning `ComposeUIViewController { App() }`.
9. Implement `iOSApp.swift` + `ContentView.swift` from Section 7.7.
10. Configure `Info.plist` for Network Extension (Section 7.3).
11. Test on a physical iOS device (Network Extensions require device; simulator not supported).

Deliverable: App runs on iOS. VPN connects/disconnects. Status updates in UI.

### Phase 7: Build Automation, Anti-Detection Polish, Release Prep (Days 21–25)

1. Write and test `xray-build/build.sh`, `build-android.sh`, `build-ios.sh` from Section 13.
2. Verify gomobile compiles real `XrayCore.aar` and `LibXray.xcframework`.
3. Replace placeholder AAR with real build artifact.
4. Test Reality fingerprinting: connect to a real VLESS+Reality server.
5. Test Kill Switch: simulate Xray crash; verify internet is blocked on Android.
6. Test Fake DNS: use Wireshark/packet capture; verify no plaintext DNS leaks.
7. Test random ports: reconnect 5 times; verify different socks ports each time.
8. Write `proguard-rules.pro` from Section 14; test release build on Android.
9. Implement watchdog coroutine in `UltraVpnService` (poll `XrayBridge.isRunning()` every 5s).
10. Add foreground notification for Android (required for foreground service).
11. Update `README.md` with build instructions.
12. Final integration test: full connect/disconnect/reconnect cycle on both platforms.

Deliverable: Release-ready MVP. Both platforms work. Anti-detection features functional. Build scripts produce reproducible artifacts.

---

## Implementation Notes for the Implementing LLM

### Critical Implementation Constraints

1. The `VpnEngine` expect class must be declared in `shared/data/commonMain` — not in `shared/domain` — because it directly orchestrates platform VPN lifecycle which is a data/infrastructure concern. Use cases in the domain layer call it through the interface contract.

2. `VpnStateHolder` is a Kotlin `object` (singleton) deliberately. Both `UltraVpnService` (running in its own Android process space) and `VpnEngine.android.kt` need to communicate through this shared state. On Android, `VpnService` runs in the same process as the app.

3. `UltraVpnService` must call `startForeground()` within 5 seconds of `onStartCommand()` on Android 12+ to avoid `ForegroundServiceStartNotAllowedException`. Call it before `XrayBridge.startXray()`.

4. The `XrayCore.aar` gomobile output puts all Java stubs in package `libxray` (lowercase), not `io.xtls.libxray`. Verify the actual package by unzipping the AAR and inspecting `classes.jar`. The `XrayBridge.kt` references must match exactly.

5. On iOS, `NETunnelProviderManager.loadAllFromPreferencesWithCompletionHandler` is async and requires bridging to a Kotlin coroutine. Use `suspendCancellableCoroutine` with `Dispatchers.Main` to bridge the callback.

6. SQLDelight `NativeSqliteDriver` on iOS requires `-lsqlite3` in Xcode **Other Linker Flags**. Add this to the `iosApp` target's build settings. Without it, the app will crash at runtime with a missing symbol error.

7. The Xray SOCKS inbound port must be the port Xray is actually listening on inside the TUN. The TUN only needs the VPN service address/routes; Xray internally handles packet routing. When using the fd-based approach (Xray reads directly from TUN fd), there is no need for Kotlin to implement a packet-forwarding loop. The SOCKS inbound is for apps that support SOCKS proxy directly (optional).

8. For the iOS split tunnel: the `InstalledAppsProvider` returns `emptyList()` on iOS. The `SettingsScreen` should check `isAndroid: Boolean` (use an expect/actual property `val Platform.isAndroid: Boolean`) and conditionally show the split tunnel section.

9. All `suspend` functions in use cases must run on `Dispatchers.IO` (for DB operations) or `Dispatchers.Default` (for CPU-bound parsing). Use `withContext` inside implementations. The ViewModels/ScreenModels launch coroutines with `screenModelScope` (Voyager's equivalent of `viewModelScope`).

10. The `VlessConfig.kt` data class must be `@Serializable` because it is serialized to JSON and stored as `config_json` TEXT in the SQLDelight DB. Use `Json { ignoreUnknownKeys = true }` for deserialization to allow forward compatibility.

---

### Critical Files for Implementation

- `/gradle/libs.versions.toml` — Version catalog; every dependency and plugin version is defined here; all modules reference it
- `/shared/data/src/commonMain/kotlin/io/nikdmitryuk/ultraclient/data/vpn/VpnEngine.kt` — The central expect class bridging shared business logic to platform VPN APIs
- `/androidApp/src/main/kotlin/io/nikdmitryuk/ultraclient/android/vpn/UltraVpnService.kt` — Android VPN service; TUN lifecycle; Xray bridge; kill switch; foreground service
- `/shared/data/src/commonMain/kotlin/io/nikdmitryuk/ultraclient/data/vpn/XrayConfigBuilder.kt` — Generates complete Xray JSON config from parsed VLESS config + anti-detect settings
- `/iosApp/NetworkExtension/PacketTunnelProvider.swift` — iOS VPN tunnel extension; LibXray integration; memory-constrained execution environment

Sources:
- [Compose Multiplatform 1.8.0 iOS Stable Release](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/)
- [SQLDelight KMP Documentation](https://sqldelight.github.io/sqldelight/2.2.1/android_sqlite/)
- [Koin 4.1 Release Notes](https://blog.kotzilla.io/koin-4.1-is-here)
- [Voyager Navigation Setup](https://voyager.adriel.cafe/setup/)
- [libXray Official Repository](https://github.com/XTLS/libXray)
- [Android VPN Developer Guide](https://developer.android.com/develop/connectivity/vpn)
- [NEPacketTunnelProvider Memory Limits Discussion](https://developer.apple.com/forums/thread/106377)
- [VLESS+Reality Configuration Reference](https://xtls.github.io/en/config/outbounds/vless.html)
- [Xray Configuration Docs](https://xtls.github.io/en/config/)
- [Koin KMP Setup Documentation](https://insert-koin.io/docs/reference/koin-core/kmp-setup/)
- [Android ProGuard/R8 Keep Rules (2025)](https://android-developers.googleblog.com/2025/11/configure-and-troubleshoot-r8-keep-rules.html)
- [KMP expect/actual Declarations](https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-expect-actual.html)
- [Xray-examples VLESS-TCP-XTLS-Vision-REALITY](https://github.com/XTLS/Xray-examples/blob/main/VLESS-TCP-XTLS-Vision-REALITY/REALITY.ENG.md)
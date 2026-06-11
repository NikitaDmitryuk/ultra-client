plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val releaseVersionName = providers.environmentVariable("ANDROID_VERSION_NAME").orElse("1.0.0")
val releaseVersionCode =
    providers
        .environmentVariable("ANDROID_VERSION_CODE")
        .map { it.toIntOrNull() ?: 1 }
        .orElse(1)
val hasReleaseSigning =
    listOf(
        "ANDROID_KEYSTORE_PATH",
        "ANDROID_KEYSTORE_PASSWORD",
        "ANDROID_KEY_ALIAS",
        "ANDROID_KEY_PASSWORD",
    ).all { !System.getenv(it).isNullOrBlank() }

android {
    namespace = "io.nikdmitryuk.ultraclient.android"
    compileSdk = 35
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        applicationId = "io.nikdmitryuk.ultraclient"
        minSdk = 26
        targetSdk = 35
        versionCode = releaseVersionCode.get()
        versionName = releaseVersionName.get()
        // A/B: при подозрении на конфликт Go resolver и системного DNS выставить true и пересобрать.
        buildConfigField("boolean", "LIBXRAY_SKIP_INIT_DNS", "false")
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(System.getenv("ANDROID_KEYSTORE_PATH"))
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
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
    implementation(libs.serialization.json)
    implementation(libs.androidx.core.ktx)

    val xrayAar = file("libs/XrayCore.aar")
    if (xrayAar.exists()) {
        implementation(files("libs/XrayCore.aar"))
    }
}

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
    implementation(libs.androidx.core.ktx)

    val xrayAar = file("libs/XrayCore.aar")
    if (xrayAar.exists()) {
        implementation(files("libs/XrayCore.aar"))
    }
}

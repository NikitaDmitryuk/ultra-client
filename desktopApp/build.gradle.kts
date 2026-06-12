import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":shared:presentation"))
            implementation(project(":shared:data"))
            implementation(project(":shared:domain"))
            implementation(compose.desktop.currentOs)
            implementation(libs.koin.core)
            implementation(libs.coroutines.swing)
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.nikdmitryuk.ultraclient.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Ultra Client"
            packageVersion = "1.0.0"
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform)   apply false
    alias(libs.plugins.kotlin.android)         apply false
    alias(libs.plugins.kotlin.serialization)   apply false
    alias(libs.plugins.android.application)    apply false
    alias(libs.plugins.android.library)        apply false
    alias(libs.plugins.sqldelight)             apply false
    alias(libs.plugins.compose.multiplatform)  apply false
    alias(libs.plugins.compose.compiler)       apply false
    id("com.github.ben-manes.versions")          version "0.54.0"
    id("nl.littlerobots.version-catalog-update") version "1.1.0"
}

fun isStable(version: String): Boolean {
    val unstableKeywords = listOf("alpha", "beta", "rc", "cr", "m", "preview", "dev", "eap")
    return unstableKeywords.none { version.lowercase().contains(it) }
}

tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    rejectVersionIf { !isStable(candidate.version) }
    outputFormatter = "json"
}


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
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# --- SQLDelight ---
-keep class app.cash.sqldelight.** { *; }
-keep class io.nikdmitryuk.ultraclient.data.local.db.** { *; }
-dontwarn app.cash.sqldelight.**

# --- Voyager ---
-keep class cafe.adriel.voyager.** { *; }
-dontwarn cafe.adriel.voyager.**

# --- Compose ---
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# --- XrayCore AAR (libXray gomobile output) ---
-keep class io.xtls.libxray.** { *; }
-keep class libxray.** { *; }
-dontwarn io.xtls.libxray.**
-dontwarn libxray.**
-keepclasseswithmembernames class * { native <methods>; }

# --- Android VPN Service ---
-keep class io.nikdmitryuk.ultraclient.android.vpn.UltraVpnService { *; }
-keep class io.nikdmitryuk.ultraclient.android.** { *; }

# --- Domain Models ---
-keep class io.nikdmitryuk.ultraclient.domain.model.** { *; }

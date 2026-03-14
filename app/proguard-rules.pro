# ============================================================
# MyWeld ProGuard / R8 Rules
# ============================================================

# ── App data layer ───────────────────────────────────────────
-keep class com.myweld.app.data.ble.** { *; }
-keep class com.myweld.app.data.model.** { *; }
-keep class com.myweld.app.data.repository.** { *; }

# ── Nordic BLE Library ───────────────────────────────────────
-keep class no.nordicsemi.android.** { *; }
-dontwarn no.nordicsemi.android.**

# ── Koin DI (reflection-based) ───────────────────────────────
-keep class org.koin.** { *; }
-keepnames class * implements org.koin.core.definition.BeanDefinition
-dontwarn org.koin.**

# ── Kotlin ───────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ── DataStore ────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ── Jetpack Compose ──────────────────────────────────────────
# Compose uses reflection to read lambda class names for tooling;
# keep anything the runtime needs.
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Vico Charts ──────────────────────────────────────────────
-keep class com.patrykandpatrick.vico.** { *; }

# ── JSON (org.json used by DeviceRepository) ─────────────────
-keep class org.json.** { *; }

# ── General Android best practices ───────────────────────────
# Keep ViewModels (referenced by Koin)
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Keep Parcelables
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# Enum safety
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

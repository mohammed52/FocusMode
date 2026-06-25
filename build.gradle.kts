// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Registered here (apply false) so the version is available, but not yet applied in
    // app/build.gradle.kts — applying it there requires a real app/google-services.json, which
    // doesn't exist until the Firebase Console app registration step is done. Sync/build stays
    // green without it; only actually applying the plugin would require the file.
    id("com.google.gms.google-services") version "4.5.0" apply false
}
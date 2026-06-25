// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Registered here (apply false) so the version is available; actually applied in
    // app/build.gradle.kts, which requires app/google-services.json (gitignored — copy it from
    // the Firebase Console app registration for com.mohammedpetiwala.masjidcallblock).
    id("com.google.gms.google-services") version "4.5.0" apply false
}
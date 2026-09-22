// App-level build file for PSBDx DevBrowser.
// Copyright (C) 2024 PSBDx DevBrowser Contributors
// Licensed under the GNU General Public License v3.0 (see LICENSE).
//
// F-Droid / FOSS compliance note: every dependency declared below is
// free and open-source software available on Maven Central / Google's
// AOSP-only artifacts. No Google Play Services, Firebase, Crashlytics,
// AdMob, or other proprietary SDKs are used anywhere in this project.

import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.devbrowser.psbdx"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.devbrowser.psbdx"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // --------------------------------------------------------------
    // Signing configuration.
    //
    // Reads keystore credentials from environment variables that are
    // injected by the GitHub Actions workflow (see
    // .github/workflows/build.yml). If they are not present (e.g. a
    // local developer build), release builds fall back safely to the
    // debug keystore so `./gradlew assembleRelease` never fails on a
    // developer's machine.
    // --------------------------------------------------------------
    val releaseStorePassword: String? = System.getenv("RELEASE_STORE_PASSWORD")
    val releaseKeyPassword: String? = System.getenv("RELEASE_KEY_PASSWORD")
    val releaseKeyAlias: String? = System.getenv("RELEASE_KEYALIAS")
    val releaseKeystoreFile = file("${project.rootDir}/release.keystore")

    val hasReleaseSigningEnv = !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        releaseKeystoreFile.exists()

    signingConfigs {
        if (hasReleaseSigningEnv) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Fall back to the debug signing config for local builds where
            // the release keystore / secrets are not available.
            signingConfig = if (hasReleaseSigningEnv) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // --- Core AndroidX / Kotlin ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // --- Jetpack Compose (Material 3) ---
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // --- WebKit (for WebViewFeature / WebSettingsCompat, still FOSS/AOSP) ---
    implementation("androidx.webkit:webkit:1.11.0")

    // --- Local persistence (Bookmarks / History) ---
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // --- Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // --- Testing ---
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

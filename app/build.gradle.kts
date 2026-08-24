plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.jarvisquest.app"

    // compileSdk 35 = Android 15 platform stubs, used only at build time.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jarvisquest.app"

        // Quest 3's Horizon OS is currently based on Android 14 (API 34) and
        // Meta has confirmed APKs built against an older target SDK than 34
        // can fail to load on-device. minSdk 29 keeps the app installable on
        // older Quest 2 units too, but targetSdk MUST stay >= 34 for Quest 3.
        minSdk = 29
        targetSdk = 34

        versionCode = 1
        versionName = "0.1.0-milestone1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // No GMS, no Play Services dependency anywhere in this module.
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            // Debug builds are signed automatically with the AGP debug
            // keystore, so `assembleDebug` already produces a directly
            // installable/sideloadable APK — no signing config needed here.
            isDebuggable = true
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // --- Core / lifecycle ---
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    // Explicit (not just transitive via -compose) so AndroidViewModel and
    // viewModelScope are guaranteed to resolve.
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    // --- Compose (UI toolkit) ---
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // --- Coroutines (async pipeline: audio -> VAD -> STT -> router -> AI -> TTS) ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // --- Testing ---
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // Deliberately NOT included in Milestone 1:
    //  - any com.google.android.gms:* artifact (Quest ships without GMS)
    //  - llama.cpp / GGUF loader (arrives with the AIService real
    //    implementation in Milestone 3, together with the NDK/CMake setup)
    //  - whisper.cpp JNI (arrives in Milestone 2, replacing
    //    AndroidSpeechToTextService as the default)
}

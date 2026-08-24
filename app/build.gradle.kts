plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.jarvisquest.app"

    // compileSdk 35 = Android 15 platform stubs, used only at build time.
    compileSdk = 35

    // Pinned so CI and any local Android Studio build resolve the exact
    // same NDK — must match the `ndk;...` package installed in
    // .github/workflows/android-build.yml.
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.jarvisquest.app"

        // Quest 3's Horizon OS is currently based on Android 14 (API 34) and
        // Meta has confirmed APKs built against an older target SDK than 34
        // can fail to load on-device. minSdk 29 keeps the app installable on
        // older Quest 2 units too, but targetSdk MUST stay >= 34 for Quest 3.
        minSdk = 29
        targetSdk = 34

        versionCode = 2
        versionName = "0.2.0-milestone2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // No GMS, no Play Services dependency anywhere in this module.
        vectorDrawables { useSupportLibrary = true }

        // Quest 3 is ARM64-only — no reason to build/ship armeabi-v7a,
        // x86, or x86_64 native libs. Also shrinks the APK now that
        // libjarvis_whisper.so + libwhisper.so are part of the build.
        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
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

    // Deliberately still NOT included as of Milestone 2:
    //  - any com.google.android.gms:* artifact (Quest ships without GMS)
    //  - llama.cpp / GGUF loader (Milestone 3 — see CMakeLists.txt comment)
    // whisper.cpp itself is a native (C++/CMake) dependency, not a Maven
    // artifact — see src/main/cpp/CMakeLists.txt and the
    // app/src/main/cpp/third_party/whisper.cpp git submodule.
}

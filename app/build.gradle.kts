plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Public URL only. Provider keys live in Cloudflare Worker secrets, never in the APK.
val quickNoteEndpoint = providers.gradleProperty("zar.quickNoteEndpoint").orElse("")
require(quickNoteEndpoint.get().isEmpty() || quickNoteEndpoint.get().matches(Regex("https://[A-Za-z0-9.-]+(?::[0-9]+)?/v1/quick-note"))) {
    "zar.quickNoteEndpoint must be the HTTPS Worker URL ending in /v1/quick-note"
}

// google-services.json is shared privately and git-ignored. Apply the plugin only when the
// file is present so teammates without it can still build and run the app.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
} else {
    logger.warn("app/google-services.json not found: Firebase services are disabled for this build.")
}

android {
    namespace = "com.example.zar"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.zar"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "QUICK_NOTE_ENDPOINT", "\"${quickNoteEndpoint.get()}\"")
    }

    // Debug builds can talk to the local Firebase Emulator Suite instead of the live project:
    //   ./gradlew connectedDebugAndroidTest -Pzar.useEmulators=true
    val useEmulators = providers.gradleProperty("zar.useEmulators").orNull == "true"

    // Where the emulated Auth/Firestore are reached from the device. The default, 10.0.2.2, is the Android emulator's
    // route to the host. A physical phone cannot use it: run `adb reverse tcp:9099 tcp:9099` and `tcp:8080 tcp:8080`
    // and build with `-Pzar.emulatorHost=127.0.0.1`. Only these three hosts are accepted, because they are the ones
    // the debug and benchmark network-security configs allow cleartext to; release builds ignore it and never use
    // the emulators.
    val emulatorHost = providers.gradleProperty("zar.emulatorHost").orElse("10.0.2.2").get()
    require(emulatorHost in setOf("10.0.2.2", "127.0.0.1", "localhost")) {
        "zar.emulatorHost must be 10.0.2.2, 127.0.0.1 or localhost (got '$emulatorHost')"
    }

    // Optional id suffix (for example ".perf") so a measurement build installs beside the real app on a phone and never
    // replaces it or shares its data. Debug and benchmark builds only; release always keeps com.example.zar. The suffix
    // needs a matching client in a local google-services.json (the real file is not touched).
    val appIdSuffix = providers.gradleProperty("zar.appIdSuffix").orNull
    require(appIdSuffix == null || appIdSuffix.matches(Regex("\\.[a-z][a-z0-9_]*"))) {
        "zar.appIdSuffix must look like .perf"
    }

    buildTypes {
        debug {
            appIdSuffix?.let { applicationIdSuffix = it }
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", useEmulators.toString())
            buildConfigField("String", "EMULATOR_HOST", "\"$emulatorHost\"")
        }
        release {
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", "false")
            buildConfigField("String", "EMULATOR_HOST", "\"10.0.2.2\"")
            optimization {
                enable = false
            }
        }
        // The Macrobenchmark target: a release-like, non-debuggable build the benchmark module can profile. It keeps
        // the debug signature so it installs on an emulator/device without release keys. Never shipped.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            appIdSuffix?.let { applicationIdSuffix = it }
            buildConfigField("boolean", "USE_FIREBASE_EMULATORS", useEmulators.toString())
            buildConfigField("String", "EMULATOR_HOST", "\"$emulatorHost\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // Production attestation. The debug provider is on debug builds only (see src/debug).
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    // Local staging for automatic SMS capture; message text is encrypted before it is stored.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

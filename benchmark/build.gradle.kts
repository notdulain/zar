plugins {
    id("com.android.test")
}

// Macrobenchmark (add-fluid-motion 6.2): a self-instrumenting test module that drives the :app `benchmark` build type
// (release-like, profileable, debug-signed) and reports FrameTimingMetric / StartupTimingMetric. It is not part of the
// shipped app. Emulator timings are labelled as such and are not a physical 60/120 Hz result.
android {
    namespace = "com.example.zar.benchmark"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The only target here is an emulator, and its timings are labelled as emulator timings (never a physical
        // 60/120 Hz claim). Suppressing the emulator guard is deliberate, not an accuracy claim.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.junit)
    implementation(libs.junit)
}

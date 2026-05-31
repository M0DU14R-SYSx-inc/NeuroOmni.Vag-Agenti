import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// --- Nexa opt-in -----------------------------------------------------------
// The Nexa SDK + OmniNeural implementation are only built when explicitly
// enabled (-PnexaEnabled=true), e.g. on the Razr. CI builds the default
// (stub-only) variant so it never depends on the native SDK or a token.
val nexaEnabled = (project.findProperty("nexaEnabled") as String?)?.toBoolean() ?: false

// NEXA_TOKEN is read from local.properties (git-ignored) and exposed via
// BuildConfig. Never hardcoded in source, never committed. Empty in CI.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val nexaToken: String = (localProps.getProperty("NEXA_TOKEN")
    ?: System.getenv("NEXA_TOKEN")
    ?: "")

android {
    namespace = "com.neuroomni.horizons"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.neuroomni.horizons"
        // Floor for the shell/stub phase; keeps CI + emulators broad. The Nexa
        // SDK itself requires minSdk 27 and a Snapdragon NPU at runtime.
        minSdk = 27
        targetSdk = 35
        versionCode = 1
        versionName = "0.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("boolean", "NEXA_ENABLED", nexaEnabled.toString())
        buildConfigField("String", "NEXA_TOKEN", "\"$nexaToken\"")
    }

    signingConfigs {
        // Stable, committed debug key so every CI/local build is signed with the
        // SAME certificate. Without this, GitHub Actions generates a fresh debug
        // key per run, and installing a new APK over an old one fails with a
        // signature-mismatch ("conflicts with an existing package") error.
        // A debug keystore is not a secret — these are the well-known defaults.
        getByName("debug") {
            storeFile = rootProject.file("app/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // The OmniNeural implementation lives in a dedicated source set that is only
    // compiled into the app when nexaEnabled is set. Default/CI builds never see it.
    if (nexaEnabled) {
        sourceSets.getByName("main").java.srcDir("src/nexa/java")
        packaging { jniLibs.useLegacyPackaging = true }
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
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.kotlinx.coroutines.android)

    // Layer 1 — API layer (Spec §4 / Architecture §5): OkHttp drives the SSE /
    // NDJSON streams; security-crypto backs the encrypted credential store.
    implementation(libs.okhttp)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.documentfile)

    // On-device OmniNeural-4B runtime. Only pulled for the Nexa-enabled build so
    // CI (default build) never resolves the native SDK. See docs/N0_V4_ARCHITECTURE_v3 §4.
    if (nexaEnabled) {
        implementation(libs.nexa.core)
    }

    debugImplementation(libs.androidx.ui.tooling)
}

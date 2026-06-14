import java.io.File
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val nexaEnabled: Boolean = (project.findProperty("nexaEnabled") as? String)?.toBoolean() ?: true

// ── sherpa-onnx voice runtime ───────────────────────────────────────────
// k2-fsa doesn't publish to Maven Central; the AAR is a GitHub release
// asset. Fetched into horizons/libs/ (gitignored) on first build.
// Provides: Moonshine ASR (STT) + Kokoro TTS with bundled espeak-ng
// phonemization — replaces the hand-rolled ORT decode loops.
val sherpaVersion = "1.13.2"
val sherpaAar: File = file("libs/sherpa-onnx-$sherpaVersion.aar")
val downloadSherpaAar = tasks.register("downloadSherpaAar") {
    outputs.file(sherpaAar)
    doLast {
        if (sherpaAar.length() > 0) return@doLast
        sherpaAar.parentFile.mkdirs()
        val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/" +
            "v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar"
        val tmp = File(sherpaAar.parentFile, "${sherpaAar.name}.part")
        URI(url).toURL().openStream().use { inp ->
            tmp.outputStream().use { out -> inp.copyTo(out) }
        }
        check(tmp.renameTo(sherpaAar)) { "Could not finalize $sherpaAar" }
        logger.lifecycle("Downloaded sherpa-onnx AAR: ${sherpaAar.length()} bytes")
    }
}

// LIGHTHOUSE: three AARs would each ship a libonnxruntime.so (Nexa 1.22.0,
// sherpa 1.24.3, Maven ORT — now removed). ORT's C API is backward
// compatible (older clients run on newer runtimes) but NOT forward
// compatible, so the single packaged copy MUST be the newest: sherpa's
// 1.24.3. pickFirst alone is non-deterministic across AARs, but the app
// module's own jniLibs source set always wins the merge — so we extract
// sherpa's copy there.
val sherpaJniDir = layout.buildDirectory.dir("sherpaJni")
val extractSherpaOrt = tasks.register<Copy>("extractSherpaOrt") {
    dependsOn(downloadSherpaAar)
    from({ zipTree(sherpaAar) }) {
        include("jni/arm64-v8a/libonnxruntime.so")
        eachFile { path = "arm64-v8a/libonnxruntime.so" }
        includeEmptyDirs = false
    }
    into(sherpaJniDir)
}

android {
    namespace = "com.horizons"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.horizons"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-phase1"
        // Razr Ultra is arm64-v8a only. Excluding x86/v7a slashes APK
        // size from ~840 MB to ~300 MB and stops download truncation.
        ndk { abiFilters += "arm64-v8a" }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("release/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    defaultConfig {
        buildConfigField("boolean", "NEXA_ENABLED", nexaEnabled.toString())
    }

    packaging {
        resources.excludes += setOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
        jniLibs {
            // LIGHTHOUSE non-negotiable: Nexa SDK's plugin loader calls dlopen()
            // with a real file path, which requires the .so files to be extracted
            // to the device filesystem at install time. AGP 7+ defaults to
            // useLegacyPackaging=false (libs stay compressed inside the APK),
            // which causes:
            //   "Cannot find libnexa_plugin_npu.so in /data/app/.../lib/arm64"
            // at NexaSdk.init(). Set to true so the install-time extractor runs.
            useLegacyPackaging = true
            // Razr Ultra is arm64 only. Strip every other ABI bundled by AARs
            // (Nexa SDK + ORT each ship libs for 4 ABIs by default).
            excludes += setOf(
                "lib/armeabi-v7a/**",
                "lib/armeabi/**",
                "lib/x86/**",
                "lib/x86_64/**",
                "lib/mips/**",
                "lib/mips64/**"
            )
            pickFirsts += setOf(
                "**/libonnxruntime.so",
                "**/libonnxruntime4j_jni.so"
            )
        }
    }

    // App-module jniLibs beat AAR copies in the pickFirst merge — this is
    // how sherpa's ORT 1.24.3 deterministically wins over Nexa's 1.22.0.
    sourceSets.getByName("main").jniLibs.srcDir(sherpaJniDir)
}

tasks.named("preBuild") {
    dependsOn(downloadSherpaAar, extractSherpaOrt)
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.okhttp)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.documentfile)
    // sherpa-onnx: Moonshine ASR + Kokoro TTS (espeak-ng bundled). Replaces
    // com.microsoft.onnxruntime:onnxruntime-android — no Java ORT users left,
    // and dropping it removes one of three competing libonnxruntime.so copies.
    implementation(files(sherpaAar))
    implementation(libs.commons.compress)

    if (nexaEnabled) {
        implementation(libs.nexa.core)
    }
}

// NOTE: previously stripped npu/htp-files-v81 and v85 to save ~300 MB
// of APK size. THAT BROKE SDK INIT — decompiled NexaSdk.init() shows
// it iterates HTP_ASSET_DIRS = [htp-files, htp-files-v81, htp-files-v85]
// at startup and tries to extract each, regardless of device HTP version.
// Removing them leaves the SDK half-initialized → Model create() returns
// garbage error codes. Strip reverted; APK is back to ~787 MB.
// TODO: tackle size via ABI splits or feature module download later.

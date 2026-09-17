/*
 * Copyright 2026 Janada Sroor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Properties

// ---------------------------------------------------------------------------
// VioMATRIXC simulation engine (libngspice.so).
//
// The engine is NOT built from source here: the app consumes pinned, audited
// release binaries from https://github.com/Janadasroor/VioMATRIXC/releases
// (version + SHA-256 in gradle/viomatrixc.properties). The JNI bridge loads
// the library at runtime via dlopen, so the .so files only need to be present
// at packaging time — this task stages them under build/ and wires the
// directory as a jniLibs source. Nothing is committed to git and no
// machine-specific paths are involved.
//
// Local override for engine development (skips download and checksum):
//   ./gradlew :app:assembleDebug -PviomatrixcLocalDir=<dir>
// or VIOMATRIXC_PREBUILT_DIR=<dir>, where <dir> holds
// <abi>/libngspice.so per ABI (e.g. from compile_android.sh output).
// ---------------------------------------------------------------------------
val viomatrixcProps = Properties().apply {
    rootProject.file("gradle/viomatrixc.properties").inputStream().use(::load)
}
val viomatrixcVersion: String =
    (findProperty("viomatrixcVersion") as String?)
        ?: System.getenv("VIOMATRIXC_VERSION")
        ?: viomatrixcProps.getProperty("version").trim()
val viomatrixcAbis = mapOf(
    "arm64-v8a" to viomatrixcProps.getProperty("sha256.arm64-v8a").trim(),
    "x86_64" to viomatrixcProps.getProperty("sha256.x86_64").trim(),
)
val viomatrixcLocalDir: String? =
    (findProperty("viomatrixcLocalDir") as String?)
        ?: System.getenv("VIOMATRIXC_PREBUILT_DIR")
val viomatrixcStaging = layout.buildDirectory.dir("viomatrixc/$viomatrixcVersion")

val fetchViomatrixc by tasks.registering {
    group = "build"
    description = "Stage pinned VioMATRIXC libngspice.so binaries (SHA-256 verified)."
    // NOTE: configuration-cache compatibility — the action below may only use
    // task inputs/outputs (serializable snapshots), never script-level vals or
    // functions, because the script object itself is not serializable.
    inputs.property("engineVersion", viomatrixcVersion)
    inputs.property("engineAbis", HashMap(viomatrixcAbis))
    inputs.property("engineLocalDir", viomatrixcLocalDir ?: "")
    outputs.dir(viomatrixcStaging)
    doLast {
        fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(8192)
                var n: Int
                while (input.read(buf).also { n = it } > 0) digest.update(buf, 0, n)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        val version = inputs.properties["engineVersion"] as String
        @Suppress("UNCHECKED_CAST")
        val abis = inputs.properties["engineAbis"] as Map<String, String>
        val localDir = (inputs.properties["engineLocalDir"] as String).ifEmpty { null }
        val dest = outputs.files.singleFile
        val local = localDir?.let(::File)?.takeIf { it.isDirectory }
        if (local != null) {
            logger.lifecycle("Using local VioMATRIXC binaries from $local (checksum skipped).")
        }
        for ((abi, sha) in abis) {
            val out = dest.resolve("$abi/libngspice.so")
            if (out.isFile && sha256Of(out).equals(sha, ignoreCase = true)) continue
            if (local != null) {
                val src = local.resolve("$abi/libngspice.so")
                require(src.isFile) { "Local override is missing $abi/libngspice.so in $local." }
                src.copyTo(out, overwrite = true)
            } else {
                val url =
                    "https://github.com/Janadasroor/VioMATRIXC/releases/download/" +
                        "v$version/libngspice-$abi.so"
                logger.lifecycle("Downloading $url")
                try {
                    out.parentFile.mkdirs()
                    URI(url).toURL().openStream().use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                } catch (e: Exception) {
                    throw GradleException(
                        "Could not download VioMATRIXC $abi engine binary. " +
                            "Check your connection, or point -PviomatrixcLocalDir " +
                            "(or VIOMATRIXC_PREBUILT_DIR) at a local build. " +
                            "Cause: ${e.message}"
                    )
                }
                val actual = sha256Of(out)
                if (!actual.equals(sha, ignoreCase = true)) {
                    out.delete()
                    throw GradleException(
                        "SHA-256 mismatch for VioMATRIXC $abi binary " +
                            "(pinned in gradle/viomatrixc.properties). " +
                            "Expected $sha, got $actual. " +
                            "Update the pin file from the release's SHA256SUMS.txt."
                    )
                }
            }
        }
    }
}

android {
    namespace = "com.jnd.ngdroid"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.jnd.ngdroid"
        // Verified floor is API 26 (Android 8.0); 24-25 were never tested
        // (incl. 32-bit libngspice coverage), so 26 is the release floor.
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17")
                arguments("-DANDROID_STL=c++_shared")
            }
        }
        ndk {
            // 32-bit armeabi-v7a covers low-RAM Android 8.0 (Go) devices.
            // No 32-bit engine prebuilt is published, so those devices use
            // the built-in engine; arm64 + x86_64 get VioMATRIXC via
            // fetchViomatrixc (staged under build/, wired below).
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86_64"))
        }
    }

    sourceSets {
        // Eager File (not a Provider): the staging path is deterministic,
        // and the legacy SourceSet API rejects Provider instances.
        getByName("main").jniLibs.directories.add(
            viomatrixcStaging.get().asFile.absolutePath
        )
    }

    signingConfigs {
        // Secrets come from the environment (CI) or ~/.gradle/gradle.properties
        // (local) — never from version control. Missing values simply leave
        // this config unusable; it is only attached to the release build type
        // when a keystore file is actually present (see buildTypes below).
        fun signingSecret(name: String): String? =
            System.getenv(name) ?: (findProperty(name) as String?)
        create("release") {
            storeFile = System.getenv("ANDROID_KEYSTORE_FILE")
                ?.let(::File)?.takeIf { it.isFile }
                ?: rootProject.file("release.keystore").takeIf { it.isFile }
            storePassword = signingSecret("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = signingSecret("ANDROID_KEY_ALIAS")
            keyPassword = signingSecret("ANDROID_KEY_PASSWORD")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            // Release signing: keystore never lives in git. CI decodes it
            // from the ANDROID_KEYSTORE_BASE64 secret to the path in
            // ANDROID_KEYSTORE_FILE; local builds use ./release.keystore
            // when present, otherwise fall back to the debug key.
            val ksFile = System.getenv("ANDROID_KEYSTORE_FILE")
                ?.let(::File)?.takeIf { it.isFile }
                ?: rootProject.file("release.keystore").takeIf { it.isFile }
            if (ksFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// Engine binaries must be staged before any packaging step.
tasks.named("preBuild") { dependsOn(fetchViomatrixc) }

// Friendly APK names: NGDroid-v1.0-debug-universal.apk instead of app-*.apk.
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters
                .firstOrNull {
                    it.filterType ==
                        com.android.build.api.variant.FilterConfiguration.FilterType.ABI
                }
                ?.identifier ?: "universal"
            val buildType = variant.buildType ?: "debug"
            output.outputFileName.set(
                output.versionName.map { v -> "NGDroid-v$v-$buildType-$abi.apk" }
            )
        }
    }
}

dependencies {
    implementation(platform(libs.kotlin.bom))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.datastore.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.markdown.renderer.coil3)
    // Coil network transport (NOT pulled in by the markdown coil3 module):
    // without it remote chat images have no fetcher and render blank.
    // Registered explicitly (not via ServiceLoader) so R8 can't strip it.
    implementation(libs.coil.network.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17")
                arguments("-DANDROID_STL=c++_shared")
            }
        }
        ndk {
            // 32-bit armeabi-v7a covers low-RAM Android 8.0 (Go) devices.
            // Requires 32-bit libngspice.so; without it those devices use the built-in engine.
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86_64"))
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

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.2.10"))
    constraints {
        implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.10")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.10")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.2.10")
        implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.10")
    }
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.datastore:datastore-core:1.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.35.0")
    implementation("com.mikepenz:multiplatform-markdown-renderer-coil3:0.35.0")
    // Coil network transport (NOT pulled in by the markdown coil3 module):
    // without it remote chat images have no fetcher and render blank.
    // Registered explicitly (not via ServiceLoader) so R8 can't strip it.
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")
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
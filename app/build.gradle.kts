plugins { id("com.android.application") }

// Optional permanent signing. CI uses a key from GitHub Actions secrets when
// configured; otherwise debug APK signing remains ephemeral on fresh runners.
// Never store a release keystore or passwords in this public repository.
val signingPath = System.getenv("AMPYEOS_SIGNING_FILE")
val signingPassword = System.getenv("AMPYEOS_SIGNING_PASSWORD")
val signingAlias = System.getenv("AMPYEOS_SIGNING_ALIAS")
val signingKeyPassword = System.getenv("AMPYEOS_SIGNING_KEY_PASSWORD")
val hasPersistentSigning = !signingPath.isNullOrBlank() &&
    !signingPassword.isNullOrBlank() && !signingAlias.isNullOrBlank() &&
    !signingKeyPassword.isNullOrBlank() && file(signingPath).isFile

android {
    namespace = "com.ampyeos.canon"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.ampyeos.canon"
        minSdk = 30
        targetSdk = 36
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild {
            cmake { cppFlags += listOf("-O2", "-std=c++17") }
        }
        versionCode = 14
        versionName = "0.8.1"
    }
    signingConfigs {
        if (hasPersistentSigning) {
            create("ampyeosStable") {
                storeFile = file(signingPath!!)
                storePassword = signingPassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
        }
    }
    buildTypes {
        release { isMinifyEnabled = false }
        debug {
            isMinifyEnabled = false
            if (hasPersistentSigning)
                signingConfig = signingConfigs.getByName("ampyeosStable")
        }
    }
    ndkVersion = "27.2.12479018"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.camera:camera-core:1.6.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("com.google.mlkit:object-detection:17.0.2")
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.31.1")
}

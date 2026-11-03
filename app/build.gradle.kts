plugins { id("com.android.application") }

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
        versionCode = 12
        versionName = "0.7.1"
    }
    buildTypes {
        release { isMinifyEnabled = false }
        debug { isMinifyEnabled = false }
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

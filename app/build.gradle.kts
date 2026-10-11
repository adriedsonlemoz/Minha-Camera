plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.minhacamera"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.minhacamera"
        minSdk = 24
        targetSdk = 34
        versionCode = 14
        versionName = "1.4.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Assinado com a chave de debug para o APK instalar direto.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    // Fallback HEVC/RTSP; LibVLC incorpora binários nativos e aumenta o APK.
    implementation("org.videolan.android:libvlc-all:3.7.7")
}

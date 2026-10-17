import java.util.zip.ZipFile

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
        versionCode = 77
        versionName = "1.4.67"
    }

    // O AAR LibVLC 3.7.7 contém as quatro ABIs; manter x86 para compatibilidade.
    // Mesmo versionCode em todos os APKs para distribuição direta e atualização.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    // A assinatura de release vem exclusivamente de Secrets do GitHub Actions,
    // nunca da chave Debug temporária do runner.
    signingConfigs {
        create("stableRelease") {
            storeType = "PKCS12"
            storeFile = file(System.getenv("MINHACAMERA_KEYSTORE_PATH") ?: "keystore-release-NAO-CONFIGURADA.p12")
            storePassword = System.getenv("MINHACAMERA_KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("MINHACAMERA_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("MINHACAMERA_KEY_PASSWORD") ?: ""
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("stableRelease")
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
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-rtsp:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    // QR local: exibe um código no transmissor e lê via câmera no receptor.
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // Fallback HEVC/RTSP; LibVLC incorpora binários nativos e aumenta o APK.
    implementation("org.videolan.android:libvlc-all:3.7.7")
}


// Verifica o AAR realmente resolvido, antes de empacotar bibliotecas nativas.
val verifyLibvlcAbis by tasks.registering {
    doLast {
        val aar = configurations.getByName("releaseRuntimeClasspath").files.single {
            it.name == "libvlc-all-3.7.7.aar"
        }
        ZipFile(aar).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toSet()
            for (abi in listOf("armeabi-v7a", "arm64-v8a", "x86_64", "x86")) {
                for (lib in listOf("libvlc.so", "libvlcjni.so", "libc++_shared.so")) {
                    check("jni/$abi/$lib" in entries) { "LibVLC sem jni/$abi/$lib" }
                }
            }
            println("LibVLC 3.7.7: quatro ABIs verificadas no AAR resolvido: ${aar.name}")
        }
    }
}
tasks.named("preBuild") { dependsOn(verifyLibvlcAbis) }

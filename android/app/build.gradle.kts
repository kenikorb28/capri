import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// A release key, only when one is given through the environment (see docs/BUILD.md). Debug builds need none.
val releaseStore = System.getenv("CAPRI_KEYSTORE")

android {
    namespace = "com.capri.assistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.capri.assistant"
        minSdk = 30
        targetSdk = 36
        versionCode = 20000
        versionName = "2.0.0"
        ndk {
            // The TECNO CL6k (Helio G99) is arm64. x86_64 keeps the Android Studio emulator working.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = System.getenv("CAPRI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CAPRI_KEY_ALIAS")
                keyPassword = System.getenv("CAPRI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseStore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    // On-device text recognition for screens and photos when her brain can't see pictures.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    testImplementation("junit:junit:4.13.2")
}

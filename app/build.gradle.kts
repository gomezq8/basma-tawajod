plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Filled in by the GitHub build. Locally they fall back to safe defaults.
val buildNumber = (System.getenv("VERSION_CODE") ?: "1").toInt()
val githubRepo = System.getenv("GITHUB_REPOSITORY") ?: ""
val keystorePath = System.getenv("KEYSTORE_PATH")

android {
    namespace = "com.basma.tawajod"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.basma.tawajod"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")
    }

    buildFeatures {
        buildConfig = true
    }

    // The same key must sign every version, or Android refuses to update over the old one.
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Map for picking the work location (OpenStreetMap, no API key needed)
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}

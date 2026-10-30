plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinCompose)
}

android {
    namespace = "net.matsudamper.sesamilock"
    compileSdk = 37

    defaultConfig {
        applicationId = "net.matsudamper.sesamilock"
        minSdk = 28
        targetSdk = 36
        val ciVersion = System.getenv("VERSION")
        versionCode = 1
        versionName = ciVersion ?: "1.0"
    }

    signingConfigs {
        val isCI = System.getenv("CI") != null
        if (isCI) {
            create("ci") {
                val keystoreFile = System.getenv("DEBUG_KEYSTORE_FILE")
                requireNotNull(keystoreFile) { "DEBUG_KEYSTORE_FILE environment variable is required in CI" }
                storeFile = rootProject.file(keystoreFile)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            val isCI = System.getenv("CI") != null
            if (isCI) {
                signingConfig = signingConfigs.getByName("ci")
            }
        }
        release {
            val isCI = System.getenv("CI") != null
            if (isCI) {
                signingConfig = signingConfigs.getByName("ci")
            }
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidxActivityCompose)
    implementation(libs.androidxComposeMaterial3)
}

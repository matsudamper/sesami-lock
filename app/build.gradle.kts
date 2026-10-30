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
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidxActivityCompose)
    implementation(libs.androidxComposeMaterial3)
}

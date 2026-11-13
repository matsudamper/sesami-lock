plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinCompose)
}

android {
    namespace = "net.matsudamper.sesamilock.widget"
    compileSdk = 37
    defaultConfig {
        minSdk = 28
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.glanceAppwidget)
    implementation(libs.kotlinxCoroutinesAndroid)
}

plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "co.candyhouse.sesame"
    compileSdk = 37
    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(libs.androidxLifecycleLivedataCore)
    api(libs.kotlinxCoroutinesCore)
    api(libs.gson)
    implementation(libs.androidxAnnotation)
}

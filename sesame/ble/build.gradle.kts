plugins {
    alias(libs.plugins.androidLibrary)
}

android {
    namespace = "net.matsudamper.sesamilock.sesame.ble"
    compileSdk = 37
    defaultConfig {
        minSdk = 28
    }
}

kotlin {
    explicitApi()
}

dependencies {
    api(project(":sesame:core"))
    implementation(project(":vendor:sesame-sdk"))
    implementation(libs.kotlinxCoroutinesAndroid)

    testImplementation(kotlin("test-junit"))
}

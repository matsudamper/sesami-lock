plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "net.matsudamper.sesamilock.sesame.credential"
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
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.kotlinxCoroutinesAndroid)

    testImplementation(kotlin("test-junit"))
}

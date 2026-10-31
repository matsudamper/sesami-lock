plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
}

dependencies {
    api(libs.kotlinxCoroutinesCore)
    implementation(libs.kotlinxSerializationJson)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinxCoroutinesTest)
}

kotlin {
    explicitApi()
}

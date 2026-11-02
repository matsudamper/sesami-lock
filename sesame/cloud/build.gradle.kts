plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    explicitApi()
}

dependencies {
    api(project(":sesame:core"))
    implementation(libs.kotlinxSerializationJson)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinxCoroutinesTest)
}

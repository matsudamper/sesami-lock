plugins {
    alias(libs.plugins.androidLibrary)
}

val upstreamSdkDir = layout.projectDirectory.dir("upstream/sesameSdk")

android {
    namespace = "co.candyhouse.sesame"
    compileSdk = 37
    defaultConfig {
        minSdk = 28
        consumerProguardFiles(upstreamSdkDir.file("consumer-rules.pro"))
    }
    buildFeatures {
        buildConfig = true
    }
    sourceSets.named("main") {
        manifest.srcFile(upstreamSdkDir.file("src/main/AndroidManifest.xml"))
        kotlin.directories.add(upstreamSdkDir.dir("src/main/java").asFile.path)
    }
}

dependencies {
    api(libs.androidxLifecycleLivedataCore)
    api(libs.kotlinxCoroutinesCore)
    api(libs.gson)
    implementation(libs.androidxAnnotation)
}

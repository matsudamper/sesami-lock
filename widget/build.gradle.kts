plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.paparazzi)
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

tasks.withType<Test>().configureEach {
    val hasPaparazziTask = gradle.startParameter.taskNames.any {
        it.lowercase().contains("paparazzi")
    }
    useJUnit {
        if (hasPaparazziTask) {
            includeCategories("net.matsudamper.sesamilock.widget.PaparazziTestCategory")
        } else {
            excludeCategories("net.matsudamper.sesamilock.widget.PaparazziTestCategory")
        }
    }
    systemProperty("paparazzi.filter", System.getProperty("paparazzi.filter", ""))
}

dependencies {
    implementation(libs.glanceAppwidget)
    implementation(libs.kotlinxCoroutinesAndroid)

    testImplementation(libs.junit4)
}

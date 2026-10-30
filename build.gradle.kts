import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.FailOnSeverity
import dev.detekt.gradle.plugin.DetektPlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jlleitschuh.gradle.ktlint.KtlintPlugin

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinCompose) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

subprojects {
    pluginManager.apply(DetektPlugin::class.java)
    pluginManager.apply(KtlintPlugin::class.java)

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        verbose.set(true)
        version.set(rootProject.libs.versions.ktlint.get())
        outputToConsole.set(true)
    }
    configure<DetektExtension> {
        config.setFrom(rootProject.files("detekt.yml"))
        parallel = true
        buildUponDefaultConfig = true
        failOnSeverity.set(FailOnSeverity.Info)
    }
    tasks.withType<Detekt>().configureEach {
        jvmTarget.set("22")
        reports {
            html.required.set(false)
            markdown.required.set(true)
            markdown.outputLocation.set(file("build/reports/detekt.txt"))
            sarif.required.set(false)
        }
    }
    plugins.withType<org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper> {
        the<org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension>().jvmToolchain(25)
    }
    tasks.withType<KotlinJvmCompile> {
        compilerOptions {
            allWarningsAsErrors.set(true)
        }
    }

    dependencies {
        "detektPlugins"(rootProject.libs.detektCompose)
    }
}

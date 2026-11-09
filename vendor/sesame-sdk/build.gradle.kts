import javax.inject.Inject

plugins {
    alias(libs.plugins.androidLibrary)
}

val upstreamSdkDir = layout.projectDirectory.dir("upstream/sesameSdk")
val patchedSourceDir = layout.projectDirectory.dir("patched")

abstract class SyncUpstreamSourcesWithoutPatchedFiles @Inject constructor(
    private val fileSystemOperations: FileSystemOperations,
) : DefaultTask() {
    @get:InputDirectory
    abstract val upstreamSourceDir: DirectoryProperty

    @get:Input
    abstract val patchedRelativePaths: SetProperty<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun sync() {
        fileSystemOperations.sync {
            from(upstreamSourceDir) { exclude(patchedRelativePaths.get()) }
            into(outputDir)
        }
    }
}

val upstreamSourcesWithoutPatchedFiles =
    tasks.register<SyncUpstreamSourcesWithoutPatchedFiles>("syncUpstreamSourcesWithoutPatchedFiles") {
        upstreamSourceDir.set(upstreamSdkDir.dir("src/main/java"))
        patchedRelativePaths.set(
            fileTree(patchedSourceDir).files.map { it.relativeTo(patchedSourceDir.asFile).invariantSeparatorsPath },
        )
    }

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
        kotlin.directories.add(patchedSourceDir.asFile.path)
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.kotlin?.addGeneratedSourceDirectory(
            upstreamSourcesWithoutPatchedFiles,
            SyncUpstreamSourcesWithoutPatchedFiles::outputDir,
        )
    }
}

dependencies {
    api(libs.androidxLifecycleLivedataCore)
    api(libs.kotlinxCoroutinesCore)
    api(libs.gson)
    implementation(libs.androidxAnnotation)
}

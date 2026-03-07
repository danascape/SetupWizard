/*
 * SPDX-FileCopyrightText: 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */


plugins {
    id("com.android.application")
    id("kotlin-android")
}

// AGP's ResourceMerger does not understand AOSP's product="tablet"/"default" attribute on
// string resources and treats them as duplicates. This task copies src/main/res into the
// build directory, dropping product="tablet" entries and stripping product="default" so the
// remaining strings look like normal unqualified resources to AGP.
abstract class PreprocessAospResourcesTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun preprocess() {
        val srcDir = sourceDir.get().asFile
        val dstDir = outputDir.get().asFile
        dstDir.deleteRecursively()
        srcDir.walkTopDown().forEach { src ->
            val dst = dstDir.resolve(src.relativeTo(srcDir))
            when {
                src.isDirectory -> dst.mkdirs()
                src.extension == "xml" && src.parentFile.name.startsWith("values") -> {
                    dst.parentFile.mkdirs()
                    var text = src.readText()
                    // Drop product="tablet" string elements (may be multi-line)
                    text = text.replace(
                        Regex("""(?s)<string\b[^>]*product="tablet"[^>]*>.*?</string>"""), ""
                    )
                    // Strip product="default" attribute from surviving strings
                    text = text.replace(""" product="default"""", "")
                    dst.writeText(text)
                }
                else -> {
                    dst.parentFile.mkdirs()
                    src.copyTo(dst, overwrite = true)
                }
            }
        }
    }
}

val preprocessAospResources by tasks.registering(PreprocessAospResourcesTask::class) {
    sourceDir.set(file("src/main/res"))
    outputDir.set(layout.buildDirectory.dir("aosp-res"))
}

android {
    compileSdk = 35

    defaultConfig {
        applicationId = "org.lineageos.setupwizard"
        minSdk = 32
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            // Includes the default ProGuard rules files.
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    namespace = "org.lineageos.setupwizard"

    sourceSets.getByName("main") {
        // Remove the original src/main/res; the preprocessed copy is wired in via
        // androidComponents below so Gradle properly tracks the task dependency.
        res.setSrcDirs(emptyList<File>())
    }
}

// Wire the preprocessed res directory as a generated source for every variant.
// This is the AGP-idiomatic way: Gradle automatically infers all task dependencies.
androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(
            preprocessAospResources,
            PreprocessAospResourcesTask::outputDir
        )
    }
}

dependencies {
    compileOnly(fileTree(mapOf("dir" to "../system_libs", "include" to listOf("*.jar"))))

    implementation(project(":setupcompat"))
    implementation(project(":setupdesign"))
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.lottie)
}

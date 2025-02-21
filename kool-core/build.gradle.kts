import java.io.FileInputStream
import java.util.*

plugins {
    //alias(commonLibs.plugins.androidLibrary)
    alias(commonLibs.plugins.kotlinMultiplatform)
    alias(commonLibs.plugins.kotlinSerialization)
    alias(commonLibs.plugins.kotlinAtomicFu)
    alias(commonLibs.plugins.kotlinDokka)

    `maven-publish`
    signing
}

kotlin {
    jvm("desktop") {
        jvmToolchain(11)
    }
    js(IR) {
        browser { }
    }

    //androidTarget {
    //    compilations.all {
    //        kotlinOptions {
    //            jvmTarget = "1.8"
    //        }
    //    }
    //}

    targets.all {
        compilations.all {
            kotlinOptions {
                freeCompilerArgs = freeCompilerArgs + "-opt-in=kotlin.RequiresOptIn"
            }
        }
    }

    sourceSets {
        val desktopMain by getting
        val desktopTest by getting

        commonMain.dependencies {
            api(commonLibs.kotlin.coroutines)
            implementation(commonLibs.kotlin.serialization.core)
            implementation(commonLibs.kotlin.serialization.json)
            implementation(commonLibs.kotlin.reflect)
            implementation(commonLibs.kotlin.atomicfu)
        }

        commonTest.dependencies {
            implementation(commonLibs.kotlin.test)
        }

        desktopMain.dependencies {
            implementation(jvmLibs.jsvg)
            api(jvmLibs.lwjgl.core)
            api(jvmLibs.lwjgl.glfw)
            api(jvmLibs.lwjgl.jemalloc)
            api(jvmLibs.lwjgl.nfd)
            api(jvmLibs.lwjgl.opengl)
            api(jvmLibs.lwjgl.shaderc)
            api(jvmLibs.lwjgl.stb)
            api(jvmLibs.lwjgl.vma)
            api(jvmLibs.lwjgl.vulkan)

            listOf("natives-linux", "natives-windows", "natives-macos", "natives-macos-arm64").forEach { platform ->
                runtimeOnly("${jvmLibs.lwjgl.core.get()}:$platform")
                runtimeOnly("${jvmLibs.lwjgl.glfw.get()}:$platform")
                runtimeOnly("${jvmLibs.lwjgl.jemalloc.get()}:$platform")
                runtimeOnly("${jvmLibs.lwjgl.nfd.get()}:$platform")
                runtimeOnly("${jvmLibs.lwjgl.opengl.get()}:$platform")
                runtimeOnly("${jvmLibs.lwjgl.shaderc.get()}:$platform")
                runtimeOnly("${jvmLibs.lwjgl.stb.get()}:$platform")
                runtimeOnly("${jvmLibs.lwjgl.vma.get()}:$platform")
            }
        }
        desktopTest.dependencies {
            implementation(commonLibs.kotlin.test.junit)
        }

        jsMain.dependencies {
            implementation(npm("pako", "2.0.4"))
        }

        //androidMain.dependencies { }
    }

    sourceSets.all {
        languageSettings {
            if (KoolBuildSettings.useK2) {
                languageVersion = "2.0"
            }

            optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
            optIn("kotlin.contracts.ExperimentalContracts")
            optIn("kotlin.io.encoding.ExperimentalEncodingApi")
            optIn("kotlin.ExperimentalStdlibApi")
        }
    }
}

//android {
//    namespace = "de.fabmax.kool"
//    compileSdk = commonLibs.versions.android.compileSdk.get().toInt()
//
//    defaultConfig {
//        minSdk = commonLibs.versions.android.minSdk.get().toInt()
//    }
//    compileOptions {
//        sourceCompatibility = JavaVersion.VERSION_1_8
//        targetCompatibility = JavaVersion.VERSION_1_8
//    }
//}

tasks.register<GenerateVariantsFromFloatPrototype>("generateDoubleAndIntVariants") {
    filesToUpdate = kotlin.sourceSets.findByName("commonMain")?.kotlin
        ?.sourceDirectories
        ?.flatMap {
            listOf(
                File(it, "de/fabmax/kool/math/Vec2.kt"),
                File(it, "de/fabmax/kool/math/Vec3.kt"),
                File(it, "de/fabmax/kool/math/Vec4.kt")
            )
        }
        ?.filter { it.exists() }
        ?.map { it.absolutePath }
        ?: emptyList()
}

tasks.register<GenerateVariantsFromFloatPrototype>("generateDoubleOnlyVariants") {
    generateIntTypes = false
    filesToUpdate = kotlin.sourceSets.findByName("commonMain")?.kotlin
        ?.sourceDirectories
        ?.flatMap {
            listOf(
                File(it, "de/fabmax/kool/math/Angle.kt"),
                File(it, "de/fabmax/kool/math/Mat3.kt"),
                File(it, "de/fabmax/kool/math/Mat4.kt"),
                File(it, "de/fabmax/kool/math/Mat4Stack.kt"),
                File(it, "de/fabmax/kool/math/Quat.kt"),
                File(it, "de/fabmax/kool/math/PointDistance.kt"),
                File(it, "de/fabmax/kool/math/Ray.kt"),
                File(it, "de/fabmax/kool/math/spatial/BoundingBox.kt"),
                File(it, "de/fabmax/kool/scene/MatrixTransform.kt"),
                File(it, "de/fabmax/kool/scene/TrsTransform.kt"),
            )
        }
        ?.filter { it.exists() }
        ?.map { it.absolutePath }
        ?: emptyList()
}

tasks.register("generateTypeVariants") {
    dependsOn("generateDoubleAndIntVariants", "generateDoubleOnlyVariants")
}

tasks.register<VersionNameUpdate>("updateVersion") {
    versionName = "$version"
    filesToUpdate = listOf(
        kotlin.sourceSets.findByName("commonMain")?.kotlin
            ?.sourceDirectories
            ?.map { File(it, "de/fabmax/kool/KoolContext.kt") }
            ?.find { it.exists() }?.absolutePath ?: ""
    )
}

tasks["compileKotlinJs"].dependsOn("updateVersion", "generateTypeVariants")
tasks["compileKotlinDesktop"].dependsOn("updateVersion", "generateTypeVariants")

publishing {
    publications {
        publications.filterIsInstance<MavenPublication>().forEach { pub ->
            pub.pom {
                name.set("kool")
                description.set("A multiplatform OpenGL / Vulkan graphics engine written in kotlin")
                url.set("https://github.com/fabmax/kool")
                developers {
                    developer {
                        name.set("Max Thiele")
                        email.set("fabmax.thiele@gmail.com")
                        organization.set("github")
                        organizationUrl.set("https://github.com/fabmax")
                    }
                }
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/fabmax/kool.git")
                    developerConnection.set("scm:git:ssh://github.com:fabmax/kool.git")
                    url.set("https://github.com/fabmax/kool/tree/main")
                }
            }

            // generating javadoc isn't supported for multiplatform projects -> add a dummy javadoc jar
            // containing the README.md to make maven central happy
            var docJarAppendix = pub.name
            val docTaskName = "dummyJavadoc${pub.name}"
            if (pub.name == "kotlinMultiplatform") {
                docJarAppendix = ""
            }
            tasks.register<Jar>(docTaskName) {
                if (docJarAppendix.isNotEmpty()) {
                    archiveAppendix.set(docJarAppendix)
                }
                archiveClassifier.set("javadoc")
                from("$rootDir/README.md")
            }
            pub.artifact(tasks[docTaskName])
        }
    }

    if (File("publishingCredentials.properties").exists()) {
        val props = Properties()
        props.load(FileInputStream("publishingCredentials.properties"))

        repositories {
            maven {
                url = if (version.toString().endsWith("-SNAPSHOT")) {
                    uri("https://oss.sonatype.org/content/repositories/snapshots")
                } else {
                    uri("https://oss.sonatype.org/service/local/staging/deploy/maven2")
                }
                credentials {
                    username = props.getProperty("publishUser")
                    password = props.getProperty("publishPassword")
                }
            }
        }

        signing {
            publications.forEach {
                sign(it)
            }
        }
    }
}

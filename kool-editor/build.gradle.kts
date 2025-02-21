import java.io.FileInputStream
import java.util.*

plugins {
    alias(commonLibs.plugins.kotlinMultiplatform)
    alias(commonLibs.plugins.kotlinAtomicFu)
    `maven-publish`
    signing
}

kotlin {
    jvm("desktop") {
        jvmToolchain(11)
    }
    js(IR) {
        binaries.library()
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(commonLibs.kotlin.coroutines)
            api(commonLibs.kotlin.serialization.core)
            api(commonLibs.kotlin.serialization.json)
            api(commonLibs.kotlin.reflect)
            api(commonLibs.kotlin.atomicfu)
            api(project(":kool-core"))
            api(project(":kool-physics"))
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")
        }
    }

    sourceSets.all {
        languageSettings {
            if (KoolBuildSettings.useK2) {
                languageVersion = "2.0"
            }
        }
    }
}

tasks["clean"].doLast {
    delete("${rootDir}/dist/kool-editor")
}

publishing {
    publications {
        publications.filterIsInstance<MavenPublication>().forEach { pub ->
            pub.pom {
                name.set("kool-editor")
                description.set("kool project editor")
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

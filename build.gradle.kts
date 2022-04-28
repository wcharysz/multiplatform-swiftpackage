import java.io.File
import java.io.FileInputStream
import java.util.*

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlinx:binary-compatibility-validator:0.9.0")
    }
}

apply(plugin = "binary-compatibility-validator")

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
    signing
}

version = "2.0.3-arm64-7"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(kotlin("gradle-plugin", "1.6.21"))
    testImplementation("io.kotest:kotest-runner-junit5:5.2.3")
    testImplementation("io.kotest:kotest-assertions-core:5.2.3")
    testImplementation("io.kotest:kotest-property:5.2.3")
    testImplementation("io.mockk:mockk:1.12.3")
    testImplementation(kotlin("gradle-plugin", "1.6.21"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8

    withJavadocJar()
    withSourcesJar()
}

tasks.withType<Test> {
    useJUnitPlatform()
}

extensions.findByName("buildScan")?.withGroovyBuilder {
    setProperty("termsOfServiceUrl", "https://gradle.com/terms-of-service")
    setProperty("termsOfServiceAgree", "yes")
}

gradlePlugin {
    plugins {
        create("pluginMaven") {
            id = "io.github.luca992.multiplatform-swiftpackage"
            implementationClass = "com.chromaticnoise.multiplatformswiftpackage.MultiplatformSwiftPackagePlugin"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("pluginMaven") {
            pom {
                groupId = "io.github.luca992.multiplatform-swiftpackage"
                artifactId = "io.github.luca992.multiplatform-swiftpackage.gradle.plugin"

                name.set("Multiplatform Swift Package")
                description.set("Gradle plugin to generate a Swift.package file and XCFramework to distribute a Kotlin Multiplatform iOS library")
                url.set(" https://github.com/luca992/multiplatform-swiftpackage")

                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        name.set("Georg Dresler")
                    }
                }
                scm {
                    connection.set("scm:git: https://github.com/luca992/multiplatform-swiftpackage.git")
                    developerConnection.set("scm:git:ssh://github.com/luca992/multiplatform-swiftpackage.git")
                    url.set(" https://github.com/luca992/multiplatform-swiftpackage")
                }
            }
        }
    }

    repositories {
        maven {
            val releasesUrl = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            val snapshotsUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
            name = "mavencentral"
            url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotsUrl else releasesUrl)
            credentials {
                username = System.getenv("SONATYPE_NEXUS_USERNAME") ?: properties["SONATYPE_NEXUS_USERNAME"].toString()
                password = System.getenv("SONATYPE_NEXUS_PASSWORD") ?: properties["SONATYPE_NEXUS_PASSWORD"].toString()
            }
        }
    }
}

signing {
    sign(publishing.publications["pluginMaven"])
}

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}

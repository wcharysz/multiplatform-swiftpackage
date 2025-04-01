import com.vanniktech.maven.publish.SonatypeHost

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(libs.binary.compatibility.validator)
    }
}

apply(plugin = "binary-compatibility-validator")

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    signing
    id("com.vanniktech.maven.publish") version "0.31.0"
}

group = "io.github.wcharysz"
version = "2.2.5"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.kotlin.gradle.plugin)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlin.gradle.plugin)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17

    withSourcesJar()
}

tasks.withType<Test> {
    useJUnitPlatform()
}

project.tasks.named("processResources", Copy::class.java) {
    // https://github.com/gradle/gradle/issues/17236
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

extensions.findByName("buildScan")?.withGroovyBuilder {
    setProperty("termsOfServiceUrl", "https://gradle.com/terms-of-service")
    setProperty("termsOfServiceAgree", "yes")
}

gradlePlugin {
    plugins {
        create("multiplatformSwiftPackage") {
            id = "io.github.wcharysz.multiplatform-swiftpackage"
            implementationClass = "com.chromaticnoise.multiplatformswiftpackage.MultiplatformSwiftPackagePlugin"
            displayName = "Multiplatform Swift Package Export"
            description = "Gradle plugin to generate a Swift.package file and XCFramework to distribute a Kotlin Multiplatform iOS library"
        }
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates(group.toString(), "multiplatform-swiftpackage", version.toString())

    pom {
        name = "Multiplatform Swift Package Export"
        description = "Gradle plugin to generate a Swift.package file and XCFramework to distribute a Kotlin Multiplatform iOS library"
        inceptionYear = "2025"
        url = "https://github.com/wcharysz/multiplatform-swiftpackage"

        licenses {
            license {
                name = "Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }

        developers {
            developer {
                name.set("Georg Dresler")
            }

            developer {
                name.set("Wojciech Charysz")
            }
        }

        scm {
            url = "https://github.com/wcharysz/multiplatform-swiftpackage"
            connection = "scm:git:https://github.com/wcharysz/multiplatform-swiftpackage.git"
            developerConnection = "scm:git:ssh://github.com/wcharysz/multiplatform-swiftpackage.git"
        }
    }
}

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}

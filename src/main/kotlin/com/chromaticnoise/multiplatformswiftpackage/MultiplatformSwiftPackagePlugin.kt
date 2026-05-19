package com.chromaticnoise.multiplatformswiftpackage

import com.chromaticnoise.multiplatformswiftpackage.domain.AppleTarget
import com.chromaticnoise.multiplatformswiftpackage.domain.getConfigurationOrThrow
import com.chromaticnoise.multiplatformswiftpackage.domain.platforms
import com.chromaticnoise.multiplatformswiftpackage.task.*

/**
 * Plugin to generate XCFramework and Package.swift file for Apple platform targets.
 */
public class MultiplatformSwiftPackagePlugin : org.gradle.api.Plugin<org.gradle.api.Project> {

    override fun apply(project: org.gradle.api.Project) {
        val extension = SwiftPackageExtension(project)
        project.extensions.add(EXTENSION_NAME, extension)

        project.afterEvaluate {
            (project.extensions.findByName("kotlin") as? org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension)?.let { kmpExtension ->
                extension.appleTargets = AppleTarget.allOf(
                    nativeTargets = kmpExtension.targets.toList(),
                    platforms = extension.targetPlatforms.platforms
                )
                // Register the lazy output tasks now. registerKotlinXCFramework is NOT called
                // here – see the projectsEvaluated hook below.
                project.registerCreateXCFrameworkTask()
                project.registerCreateZipFileTask()
                project.registerCreateSwiftPackageTask()
            }
        }

        // registerKotlinXCFramework (which calls XCFramework() → tasks.register internally)
        // must run AFTER every project's afterEvaluate callbacks have completed, including
        // the Kotlin CocoaPods plugin's. That plugin registers pod framework binaries
        // (e.g. linkPodReleaseFrameworkIosArm64) in its own afterEvaluate; if we run before
        // it, nativeTarget.binaries won't yet contain the pod binary and getFramework() falls
        // back to the plain linkReleaseFramework* variant which lacks CocoaPods framework
        // search paths, causing "ld: framework 'LockAccessLib' not found".
        //
        // gradle.projectsEvaluated fires after ALL afterEvaluate callbacks for all projects
        // but is still in the configuration phase, so tasks.register() is permitted there.
        project.gradle.projectsEvaluated {
            if (extension.appleTargets.isNotEmpty()) {
                val configuration = project.getConfigurationOrThrow()
                project.registerKotlinXCFramework(configuration)
            }
        }
    }

    internal companion object {
        internal const val EXTENSION_NAME = "multiplatformSwiftPackage"
    }
}

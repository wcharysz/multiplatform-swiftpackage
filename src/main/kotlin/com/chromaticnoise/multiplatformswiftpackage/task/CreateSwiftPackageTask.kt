package com.chromaticnoise.multiplatformswiftpackage.task

import com.chromaticnoise.multiplatformswiftpackage.domain.*
import groovy.text.SimpleTemplateEngine
import org.gradle.api.Project
import java.io.File

internal fun Project.registerCreateSwiftPackageTask() {
    tasks.register("createSwiftPackage") {
        group = "multiplatform-swift-package"
        description = "Creates a Swift package to distribute an XCFramework"

        dependsOn("createXCFramework")
        dependsOn("createZipFile")

        doLast {
            val (configuration, configMs) = measureExecutionMs {
                getConfigurationOrThrow()
            }

            val (packageFile, fileInitMs) = measureExecutionMs {
                File(configuration.outputDirectory.value, SwiftPackageConfiguration.FILE_NAME).apply {
                    parentFile.mkdirs()
                    createNewFile()
                }
            }

            val (checksum, checksumMs) = measureExecutionMs {
                zipFileChecksum(project, configuration.outputDirectory, configuration.zipFileName)
            }

            val (packageConfiguration, modelMs) = measureExecutionMs {
                SwiftPackageConfiguration(
                    project = project,
                    packageName = configuration.packageName,
                    toolVersion = configuration.swiftToolsVersion,
                    platforms = platforms(configuration),
                    distributionMode = configuration.distributionMode,
                    zipChecksum = checksum,
                    zipFileName = configuration.zipFileName,
                    libraryType = configuration.libraryType
                )
            }

            val (_, renderMs) = measureExecutionMs {
                SimpleTemplateEngine()
                    .createTemplate(SwiftPackageConfiguration.templateFile)
                    .make(packageConfiguration.templateProperties)
                    .writeTo(packageFile.writer())
            }

            logger.lifecycle(
                "[multiplatform-swift-package][timing] createSwiftPackage phases " +
                    "(config=${configMs}ms, fileInit=${fileInitMs}ms, checksum=${checksumMs}ms, " +
                    "model=${modelMs}ms, render=${renderMs}ms) | output=${packageFile.absolutePath}"
            )
        }

        addTimingLogger("createSwiftPackage")
    }
}

private fun platforms(configuration: PluginConfiguration): String = configuration.targetPlatforms.flatMap { platform ->
    configuration.appleTargets
        .filter { appleTarget -> platform.targets.firstOrNull { it.konanTarget == appleTarget.nativeTarget.konanTarget } != null }
        .mapNotNull { target -> target.nativeTarget.konanTarget.family.swiftPackagePlatformName }
        .distinct()
        .map { platformName -> ".$platformName(.v${platform.version.name})" }
}.joinToString(",\n")

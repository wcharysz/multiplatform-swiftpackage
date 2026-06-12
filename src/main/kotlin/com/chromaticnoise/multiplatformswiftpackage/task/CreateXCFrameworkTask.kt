package com.chromaticnoise.multiplatformswiftpackage.task

import com.chromaticnoise.multiplatformswiftpackage.domain.*
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.tasks.FatFrameworkTask
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject


internal fun getMacosFrameworks(configuration: PluginConfiguration): List<AppleFramework> {
    return configuration.appleTargets.mapNotNull { it.getFramework(configuration.buildConfiguration) }
        .filter {
            it.linkTask.name.contains("MacosX64") || it.linkTask.name.contains("MacosArm64")
        }
}

internal fun getIosSimulatorFrameworks(configuration: PluginConfiguration): List<AppleFramework> {
    return configuration.appleTargets.mapNotNull { it.getFramework(configuration.buildConfiguration) }
        .filter {
            it.linkTask.name.contains("IosX64") || it.linkTask.name.contains("IosSimulatorArm64")
        }
}

internal fun getWatchosSimulatorFrameworks(configuration: PluginConfiguration): List<AppleFramework> {
    return configuration.appleTargets.mapNotNull { it.getFramework(configuration.buildConfiguration) }
        .filter {
            it.linkTask.name.contains("WatchosX86")
                    || it.linkTask.name.contains("WatchosX64")
                    || it.linkTask.name.contains("WatchosDeviceArm64")
                    || it.linkTask.name.contains("WatchosSimulatorArm64")
        }
}

internal fun getTvosSimulatorFrameworks(configuration: PluginConfiguration): List<AppleFramework> {
    return configuration.appleTargets.mapNotNull { it.getFramework(configuration.buildConfiguration) }
        .filter {
            it.linkTask.name.contains("TvosX64") || it.linkTask.name.contains("TvosSimulatorArm64")
        }
}

internal fun Project.registerCreateUniversalMacosFrameworkTask() =
    tasks.register<FatFrameworkTask>("createUniversalMacosFramework") {
        group = "multiplatform-swift-package"
        description = "Creates a universal (fat) macos framework"
        val configuration = getConfigurationOrThrow()
        val targets = getMacosFrameworks(configuration)
        onlyIf { targets.size > 1 }
        dependsOn(targets.map { it.linkTask.name })
        if (targets.isNotEmpty()) {
            val buildType = if (targets[0].linkTask.name.contains("Release")) "release" else "debug"
            baseName = checkNotNull(targets.first().name.value)
            destinationDirProperty.set(layout.buildDirectory.dir("bin/macosUniversal/${buildType}Framework"))
            from(targets.mapNotNull { it.framework })
        }
        addTimingLogger("createUniversalMacosFramework") { "targets=${targets.size}" }
    }

internal fun Project.registerCreateUniversalIosSimulatorFrameworkTask() =
    tasks.register<FatFrameworkTask>("createUniversalIosSimulatorFramework") {
        group = "multiplatform-swift-package"
        description = "Creates a universal (fat) ios simulator framework"
        val configuration = getConfigurationOrThrow()
        val targets = getIosSimulatorFrameworks(configuration)
        onlyIf { targets.size > 1 }
        dependsOn(targets.map { it.linkTask.name })
        if (targets.isNotEmpty()) {
            val buildType = if (targets[0].linkTask.name.contains("Release")) "release" else "debug"
            baseName = checkNotNull(targets.first().name.value)
            destinationDirProperty.set(layout.buildDirectory.dir("bin/iosSimulatorUniversal/${buildType}Framework"))
            from(targets.mapNotNull { it.framework })
        }
        addTimingLogger("createUniversalIosSimulatorFramework") { "targets=${targets.size}" }
    }

internal fun Project.registerCreateUniversalWatchosSimulatorFrameworkTask() =
    tasks.register("createUniversalWatchosSimulatorFramework", FatFrameworkTask::class.java) {
        group = "multiplatform-swift-package"
        description = "Creates a universal (fat) watchos simulator framework"
        val configuration = getConfigurationOrThrow()
        val targets = getWatchosSimulatorFrameworks(configuration)
        onlyIf { targets.size > 1 }
        dependsOn(targets.map { it.linkTask.name })
        if (targets.isNotEmpty()) {
            val buildType = if (targets[0].linkTask.name.contains("Release")) "release" else "debug"
            baseName = checkNotNull(targets.first().name.value)
            destinationDirProperty.set(layout.buildDirectory.dir("bin/watchosSimulatorUniversal/${buildType}Framework"))
            from(targets.mapNotNull { it.framework })
        }
        addTimingLogger("createUniversalWatchosSimulatorFramework") { "targets=${targets.size}" }
    }

internal fun Project.registerCreateUniversalTvosSimulatorFrameworkTask() =
    tasks.register("createUniversalTvosSimulatorFramework", FatFrameworkTask::class.java) {
        group = "multiplatform-swift-package"
        description = "Creates a universal (fat) tvos simulator framework"
        val configuration = getConfigurationOrThrow()
        val targets = getTvosSimulatorFrameworks(configuration)
        onlyIf { targets.size > 1 }
        dependsOn(targets.map { it.linkTask.name })
        if (targets.isNotEmpty()) {
            val buildType = if (targets[0].linkTask.name.contains("Release")) "release" else "debug"
            baseName = checkNotNull(targets.first().name.value)
            destinationDirProperty.set(layout.buildDirectory.dir("bin/tvosSimulatorUniversal/${buildType}Framework"))
            from(targets.mapNotNull { it.framework })
        }
        addTimingLogger("createUniversalTvosSimulatorFramework") { "targets=${targets.size}" }
    }


internal fun removeMonoFrameworksAndAddUniversalFrameworkIfNeeded(
    binFolderPrefix: String,
    buildDir: File,
    monoFrameworks: List<AppleFramework>,
    outputFrameworks: MutableList<AppleFramework>
) {
    if (monoFrameworks.size > 1) {
        monoFrameworks.forEach { mono ->
            outputFrameworks.removeIf { mono.outputFile == it.outputFile }
        }
        val frameworkName = monoFrameworks[0].name
        val frameworkNameLegalChars = frameworkName.value.replace("-", "_")
        val buildType = if (monoFrameworks[0].linkTask.name.contains("Release")) "release" else "debug"
        val destinationDir = buildDir.resolve("bin/${binFolderPrefix}Universal/${buildType}Framework")
        val outputFile = AppleFrameworkOutputFile(File(destinationDir, "${frameworkNameLegalChars}.framework"))
        outputFrameworks.add(
            AppleFramework(
                outputFile,
                frameworkName,
                AppleFrameworkLinkTask("")
            )
        )
    }
}


internal fun Project.registerCreateXCFrameworkTask() = tasks.register<CreateXCFrameworkTaskImpl>("createXCFramework") {
    group = "multiplatform-swift-package"
    description = "Creates an XCFramework for all declared Apple targets"

    val configuration = getConfigurationOrThrow()
    val xcFrameworkDestination =
        File(configuration.outputDirectory.value, "${configuration.packageName.value}.xcframework")
    val outputFrameworks =
        configuration.appleTargets.mapNotNull { it.getFramework(configuration.buildConfiguration) }.toMutableList()

    dependsOn(outputFrameworks.map { it.linkTask.name })

    val macosFrameworks = getMacosFrameworks(configuration)
    if (macosFrameworks.size > 1) {
        dependsOn("createUniversalMacosFramework")
    }
    removeMonoFrameworksAndAddUniversalFrameworkIfNeeded(
        "macos",
        layout.buildDirectory.asFile.get(),
        macosFrameworks,
        outputFrameworks
    )

    val iosSimulatorFrameworks = getIosSimulatorFrameworks(configuration)
    if (iosSimulatorFrameworks.size > 1) {
        dependsOn("createUniversalIosSimulatorFramework")
    }
    removeMonoFrameworksAndAddUniversalFrameworkIfNeeded(
        "iosSimulator",
        layout.buildDirectory.asFile.get(),
        iosSimulatorFrameworks,
        outputFrameworks
    )

    val watchosSimulatorFrameworks = getWatchosSimulatorFrameworks(configuration)
    if (watchosSimulatorFrameworks.size > 1) {
        dependsOn("createUniversalWatchosSimulatorFramework")
    }
    removeMonoFrameworksAndAddUniversalFrameworkIfNeeded(
        "watchosSimulator",
        layout.buildDirectory.asFile.get(),
        watchosSimulatorFrameworks,
        outputFrameworks
    )

    val tvosSimulatorFrameworks = getTvosSimulatorFrameworks(configuration)
    if (tvosSimulatorFrameworks.size > 1) {
        dependsOn("createUniversalTvosSimulatorFramework")
    }
    removeMonoFrameworksAndAddUniversalFrameworkIfNeeded(
        "tvosSimulator",
        layout.buildDirectory.asFile.get(),
        tvosSimulatorFrameworks,
        outputFrameworks
    )

    val frameworkFilePaths = outputFrameworks.map { it.outputFile.path }
    val debugSymbolFilePaths = outputFrameworks.map { it.dsymFile.absolutePath }

    onlyIf { outputFrameworks.isNotEmpty() }

    frameworkFiles.from(frameworkFilePaths)
    debugSymbolFiles.from(debugSymbolFilePaths)
    orderedFrameworkPaths.set(frameworkFilePaths)
    orderedDebugSymbolPaths.set(debugSymbolFilePaths)
    destinationDirectory.set(xcFrameworkDestination)
    frameworkCount.set(outputFrameworks.size)
}

internal abstract class CreateXCFrameworkTaskImpl : DefaultTask() {

    @get:Inject
    protected abstract val execOperations: ExecOperations

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val frameworkFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val debugSymbolFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val destinationDirectory: DirectoryProperty

    @get:Input
    abstract val orderedFrameworkPaths: ListProperty<String>

    @get:Input
    abstract val orderedDebugSymbolPaths: ListProperty<String>

    @get:Input
    abstract val frameworkCount: org.gradle.api.provider.Property<Int>

    @TaskAction
    fun createXCFramework() {
        val destination = destinationDirectory.get().asFile
        val (_, cleanupMs) = measureExecutionMs {
            destination.deleteRecursively()
        }

        val frameworkPaths = orderedFrameworkPaths.get()
        val debugSymbolPaths = orderedDebugSymbolPaths.get()
        val (args, argumentBuildMs) = measureExecutionMs {
            mutableListOf("-create-xcframework", "-output", destination.absolutePath).apply {
                frameworkPaths.forEachIndexed { index, frameworkPath ->
                    add("-framework")
                    add(frameworkPath)

                    val dsymPath = debugSymbolPaths.getOrNull(index)
                    if (dsymPath != null && File(dsymPath).exists()) {
                        add("-debug-symbols")
                        add(dsymPath)
                    }
                }
            }
        }

        val (_, xcodebuildMs) = measureExecutionMs {
            execOperations.exec {
                executable = "xcodebuild"
                this.args(args)
            }.assertNormalExitValue()
        }

        val totalMs = cleanupMs + argumentBuildMs + xcodebuildMs
        logger.lifecycle(
            "[multiplatform-swift-package][timing] createXCFramework total=${totalMs}ms " +
                "(cleanup=${cleanupMs}ms, buildArgs=${argumentBuildMs}ms, xcodebuild=${xcodebuildMs}ms) " +
                "| frameworks=${frameworkCount.get()}, debugSymbols=${debugSymbolPaths.count { File(it).exists() }}"
        )
    }
}

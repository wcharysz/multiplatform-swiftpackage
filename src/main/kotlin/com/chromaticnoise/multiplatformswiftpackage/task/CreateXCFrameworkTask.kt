package com.chromaticnoise.multiplatformswiftpackage.task

import com.chromaticnoise.multiplatformswiftpackage.domain.*
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.services.BuildServiceRegistration
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFrameworkTask
import java.io.File
import java.util.Properties

internal fun Project.registerKotlinXCFramework(configuration: PluginConfiguration) {
    val packageName = configuration.packageName.value

    // Guard against re-registering only OUR specific tasks (named after packageName).
    // We intentionally do NOT guard against consumer tasks with a different name (e.g.
    // "assembleMobilecredentialReleaseXCFramework" when packageName="MCSdk") so that we
    // always create "assembleMCSdkReleaseXCFramework" using non-pod binaries with correct
    // framework search paths injected.
    if (tasks.names.contains("assemble${packageName}ReleaseXCFramework") ||
        tasks.names.contains("assemble${packageName}DebugXCFramework")) return

    // Actively configure KGP's KotlinNativeBundleBuildService maxParallelUsages so that
    // all K/N compile and link tasks for different targets can run concurrently.
    // KGP registers the service in afterEvaluate; we run in projectsEvaluated (after all
    // afterEvaluate callbacks), so the registration exists by the time we get here and
    // maxParallelUsages has not yet been finalized (finalization happens at task execution).
    // We also emit a warning as a fallback so the consumer can configure gradle.properties
    // manually if the direct configuration is not possible for some reason.
    enforceNativeParallelism(configuration.appleTargets.size)

    runCatching {
        val xcFramework = XCFramework(packageName)
        configuration.appleTargets
            .mapNotNull { it.getFramework(configuration.buildConfiguration, preferPod = false) }
            .forEach { appleFramework ->
                appleFramework.framework?.let { framework ->
                    xcFramework.add(framework)
                    // Inject CocoaPods framework search paths into the non-pod link task so
                    // pods declared via the Kotlin CocoaPods plugin (e.g. LockAccessLib) are
                    // found by the linker.  The KMP CocoaPods plugin only configures search
                    // paths on its own "pod" binary; the regular (non-pod) binary used here
                    // for the Swift Package XCFramework does not receive them otherwise, causing
                    // "ld: framework 'X' not found" at link time.
                    injectCocoaPodsFrameworkSearchPaths(framework)
                }
            }
    }.getOrElse { throwable ->
        if (throwable.message?.contains("already exists", ignoreCase = true) == true) return
        throw throwable
    }
}

/**
 * Enforces parallel Kotlin/Native compilation for multi-target builds.
 *
 * KGP's `KotlinNativeBundleBuildService` controls how many K/N compiler invocations
 * (i.e. `compileKotlin*` + `linkRelease*` tasks) can run concurrently.  Its
 * `maxParallelUsages` defaults to **1**, serialising all targets onto a single Gradle
 * worker even when `org.gradle.parallel=true` and `org.gradle.workers.max` are both
 * set generously.
 *
 * This function takes a two-pronged approach:
 *
 * 1. **Direct service configuration** (primary path) — iterates all Gradle shared-build-
 *    service registrations at the end of the configuration phase (inside
 *    `projectsEvaluated`) and raises `maxParallelUsages` on any registration whose name
 *    matches KGP's native-bundle service.  At this point the configuration phase is still
 *    active, so `maxParallelUsages` has not yet been finalized by Gradle.
 *
 * 2. **Fallback warning** — if no matching service is found (e.g. KGP was upgraded and
 *    renamed it), emits a Gradle lifecycle warning directing the consumer to set
 *    `kotlin.native.parallelism` in `gradle.properties`.
 *
 * Note: raising `maxParallelUsages` above 1 only takes effect when
 * `org.gradle.parallel=true` is also set (Gradle will not schedule tasks in parallel
 * otherwise).
 */
private fun Project.enforceNativeParallelism(numTargets: Int) {
    if (numTargets <= 1) return

    var configured = false

    // KGP registers KotlinNativeBundleBuildService in afterEvaluate; we are in
    // projectsEvaluated so all registrations already exist — forEach processes them
    // all immediately without needing to listen for future registrations.
    @Suppress("UNCHECKED_CAST")
    (gradle.sharedServices.registrations as Iterable<BuildServiceRegistration<*, *>>).forEach { registration ->
        val regName = registration.name
        // Match KGP's service by name fragments that have been stable across KGP versions.
        if (regName.contains("NativeBundle", ignoreCase = true) ||
            regName.contains("KotlinNativeBundle", ignoreCase = true)
        ) {
            runCatching {
                val current = registration.maxParallelUsages.orNull ?: 1
                if (current < numTargets) {
                    registration.maxParallelUsages.set(numTargets)
                    logger.lifecycle(
                        "multiplatform-swiftpackage: raised '$regName' maxParallelUsages " +
                        "$current → $numTargets to allow parallel K/N compilation."
                    )
                } else {
                    logger.info(
                        "multiplatform-swiftpackage: '$regName' maxParallelUsages=$current " +
                        "already covers $numTargets targets — no change needed."
                    )
                }
                configured = true
            }.onFailure { ex ->
                logger.warn(
                    "multiplatform-swiftpackage: could not set maxParallelUsages on " +
                    "'$regName' (property may already be finalized): ${ex.message}"
                )
            }
        }
    }

    // Fallback: if the service was not found (e.g. KGP renamed it), advise the consumer
    // to configure the property manually so KGP picks it up at startup.
    if (!configured) {
        val propertyValue = findProperty("kotlin.native.parallelism")?.toString()?.toIntOrNull()
        if (propertyValue == null || propertyValue < numTargets) {
            val current = if (propertyValue == null) "not set (defaults to 1)" else "$propertyValue"
            logger.warn(
                """
                |multiplatform-swiftpackage ⚠  Could not locate KGP's KotlinNativeBundleBuildService
                |to configure parallel K/N compilation automatically.
                |kotlin.native.parallelism is $current but you have $numTargets Kotlin/Native targets.
                |All compile and link tasks may run sequentially, making the build slower than necessary.
                |
                |Add the following to your gradle.properties:
                |  kotlin.native.parallelism=$numTargets
                |  org.gradle.parallel=true
                """.trimMargin()
            )
        }
    }
}

/**
 * Reads the CocoaPods build-settings properties files generated by the Kotlin CocoaPods
 * plugin and appends any `FRAMEWORK_SEARCH_PATHS` entries as `-F<path>` linker options
 * directly to [framework]'s [linkerOpts][Framework.linkerOpts].
 *
 * This resolves "ld: framework 'PodName' not found" errors that arise when the regular
 * (non-pod) link task compiles a framework that depends on CocoaPods pods: the KMP
 * CocoaPods plugin only wires `-F` paths to the *pod* binary's link task, so the
 * non-pod binary that `multiplatform-swiftpackage` uses would otherwise lack them.
 *
 * The build-settings files are written by the `podBuildSettings*` tasks and persist
 * across builds, so reading them during the configuration phase is safe for incremental
 * builds (the common case).  On a completely fresh environment the files may not yet
 * exist; in that case this function is a no-op and the user should run the CocoaPods
 * setup tasks first (the usual CocoaPods workflow).
 */
private fun Project.injectCocoaPodsFrameworkSearchPaths(framework: Framework) {
    val buildSettingsDir = layout.buildDirectory.dir("cocoapods/buildSettings").get().asFile
    if (!buildSettingsDir.isDirectory) return

    // Map from Kotlin native target name to the platform prefix used in the properties
    // file names generated by the KMP CocoaPods plugin.
    val targetName = framework.target.name  // e.g. "iosArm64", "iosSimulatorArm64", "iosX64"
    val platformPrefix = when {
        targetName.contains("Simulator", ignoreCase = true) ||
                targetName.contains("X64", ignoreCase = true) -> "iosSimulator"
        targetName.startsWith("ios", ignoreCase = true) -> "ios"
        targetName.startsWith("macos", ignoreCase = true) -> "macos"
        targetName.startsWith("tvos", ignoreCase = true) ->
            if (targetName.contains("Simulator", ignoreCase = true)) "tvosSimulator" else "tvos"
        targetName.startsWith("watchos", ignoreCase = true) ->
            if (targetName.contains("Simulator", ignoreCase = true)) "watchosSimulator" else "watchos"
        else -> return
    }

    val settingsFiles = buildSettingsDir.listFiles { _, name ->
        name.startsWith("build-settings-$platformPrefix-") && name.endsWith(".properties")
    } ?: return

    val frameworkSearchPaths = mutableListOf<String>()
    settingsFiles.forEach { file ->
        val props = Properties()
        file.inputStream().use { props.load(it) }
        props.getProperty("FRAMEWORK_SEARCH_PATHS")
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            ?.let { frameworkSearchPaths.addAll(it) }
    }

    if (frameworkSearchPaths.isNotEmpty()) {
        framework.linkerOpts(frameworkSearchPaths.map { "-F$it" })
    }
}

internal fun Project.registerCreateXCFrameworkTask() = tasks.register("createXCFramework") {
    group = "multiplatform-swift-package"
    description = "Creates an XCFramework for all declared Apple targets"

    val configuration = getConfigurationOrThrow()
    val packageName = configuration.packageName.value
    val nativeBuildType = configuration.buildConfiguration.toNativeBuildTypeOrThrow()
    val xcFrameworkDestination =
        File(configuration.outputDirectory.value, "$packageName.xcframework")

    // Match the XCFrameworkTask registered by registerKotlinXCFramework (called in
    // projectsEvaluated before this task configuration action ever runs), or a pre-existing
    // one from the Kotlin CocoaPods plugin. Both cases are handled by the baseName filter.
    val kotlinXcFrameworkTasks = tasks.withType(XCFrameworkTask::class.java).matching { task ->
        task.buildType == nativeBuildType && task.baseName.orNull == packageName
    }
    dependsOn(kotlinXcFrameworkTasks)

    doLast {
        val xcTaskList = kotlinXcFrameworkTasks.toList()
        if (xcTaskList.isEmpty()) {
            throw GradleException(
                "No XCFrameworkTask found for build type '${nativeBuildType.getName()}' and " +
                "baseName '$packageName'. This is a bug in multiplatform-swiftpackage — please " +
                "report it at https://github.com/wcharysz/multiplatform-swiftpackage/issues"
            )
        }
        val xcTask = xcTaskList.first()
        val generatedXcFramework = xcTask.outputs.files.files
            .firstOrNull { it.name.endsWith(".xcframework") }
            ?: xcTask.outputs.files.singleFile

        xcFrameworkDestination.deleteRecursively()
        if (!generatedXcFramework.exists()) {
            throw GradleException("Generated XCFramework not found at '${generatedXcFramework.path}'")
        }
        generatedXcFramework.copyRecursively(target = xcFrameworkDestination, overwrite = true)
    }
}

private fun BuildConfiguration.toNativeBuildTypeOrThrow(): NativeBuildType = when (this) {
    BuildConfiguration.Release -> NativeBuildType.RELEASE
    BuildConfiguration.Debug -> NativeBuildType.DEBUG
    is BuildConfiguration.Custom -> when {
        configurationName.equals(NativeBuildType.RELEASE.getName(), ignoreCase = true) -> NativeBuildType.RELEASE
        configurationName.equals(NativeBuildType.DEBUG.getName(), ignoreCase = true) -> NativeBuildType.DEBUG
        else -> throw GradleException(
            "Unsupported buildConfiguration '$configurationName'. Only Debug and Release are supported for XCFramework assembly."
        )
    }
}

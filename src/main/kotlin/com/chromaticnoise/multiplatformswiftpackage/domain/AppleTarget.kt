package com.chromaticnoise.multiplatformswiftpackage.domain

import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeOutputKind

internal class AppleTarget private constructor(val nativeTarget: KotlinNativeTarget) {

    /**
     * Returns the framework binary for [buildConfiguration].
     *
     * @param preferPod when `true` (default) the CocoaPods pod binary is sorted first so
     *   it is preferred for XCFramework assembly (it carries pod linker options).
     *   Pass `false` when deriving the *package name* so that the user-configured
     *   framework binary (baseName set by the consumer, e.g. "MCSdk") is used instead
     *   of the CocoaPods-generated pod binary whose baseName is the Kotlin project name
     *   (e.g. "mobilecredential").
     */
    internal fun getFramework(buildConfiguration: BuildConfiguration, preferPod: Boolean = true): AppleFramework? =
        try {
            val nativeBinary = nativeTarget.binaries
                .filter { binary ->
                    binary.buildType.getName().equals(buildConfiguration.name, ignoreCase = true) &&
                        binary.outputKind == NativeOutputKind.FRAMEWORK
                }
                .sortedByDescending { binary ->
                    val isPod = binary.linkTaskName.contains("Pod")
                    if (preferPod) isPod else !isPod
                }
                .firstOrNull()
            AppleFramework.of(nativeBinary)
        } catch (_: Exception) { null }

    internal companion object {
        fun allOf(
            nativeTargets: Collection<KotlinTarget>,
            platforms: Collection<TargetPlatform>
        ): Collection<AppleTarget> = nativeTargets
            .filterIsInstance<KotlinNativeTarget>()
            .filter { it.konanTarget.family.isAppleFamily }
            .filter { target ->
                platforms
                    .flatMap { platform -> platform.targets.map { it.konanTarget } }
                    .firstOrNull { it == target.konanTarget } != null
            }
            .map { AppleTarget(it) }
    }
}

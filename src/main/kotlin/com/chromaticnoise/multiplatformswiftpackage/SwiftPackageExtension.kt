package com.chromaticnoise.multiplatformswiftpackage

import com.chromaticnoise.multiplatformswiftpackage.domain.*
import com.chromaticnoise.multiplatformswiftpackage.domain.PluginConfiguration.PluginConfigurationError
import com.chromaticnoise.multiplatformswiftpackage.dsl.BuildConfigurationDSL
import com.chromaticnoise.multiplatformswiftpackage.dsl.DistributionModeDSL
import com.chromaticnoise.multiplatformswiftpackage.dsl.TargetPlatformDsl
import groovy.lang.Closure
import org.gradle.api.Project
import org.gradle.api.Action
import java.io.File

public open class SwiftPackageExtension(internal val project: Project) {

    internal var buildConfiguration: BuildConfiguration = BuildConfiguration.Release
    internal var packageName: Either<PluginConfigurationError, PackageName>? = null
    internal var outputDirectory: OutputDirectory = OutputDirectory(File(project.projectDir, "swiftpackage"))
    internal var swiftToolsVersion: SwiftToolVersion? = null
    internal var distributionMode: DistributionMode = DistributionMode.Local
    internal var targetPlatforms: Collection<Either<List<PluginConfigurationError>, TargetPlatform>> = emptyList()
    internal var appleTargets: Collection<AppleTarget> = emptyList()
    internal var zipFileName: Either<PluginConfigurationError, ZipFileName>? = null
    internal var libraryType: Either<PluginConfigurationError, LibraryType>? = null

    /**
     * Sets the name of the Swift package.
     * Defaults to the base name of the first framework found in the project.
     *
     * @param name of the Swift package.
     */
    public fun packageName(name: String) {
        packageName = PackageName.of(name)
    }

    /**
     * Sets the directory where files like the Package.swift and XCFramework will be created.
     * Defaults to $projectDir/swiftpackage
     *
     * @param directory where the files will be created.
     */
    public fun outputDirectory(directory: File) {
        outputDirectory = OutputDirectory((directory))
    }

    /**
     * Version of the Swift tools. That's the version added to the Package.swift header.
     * E.g. 5.3
     */
    public fun swiftToolsVersion(name: String) {
        swiftToolsVersion = SwiftToolVersion.of(name)
    }

    /**
     * Builder for the [BuildConfiguration].
     */
    public fun buildConfiguration(configure: BuildConfigurationDSL.() -> Unit) {
        BuildConfigurationDSL().also { dsl ->
            dsl.configure()
            buildConfiguration = dsl.buildConfiguration
        }
    }

    public fun buildConfiguration(configure: Closure<BuildConfigurationDSL>) {
        val dsl = BuildConfigurationDSL()
        configure.delegate = dsl
        configure.call()
        buildConfiguration = dsl.buildConfiguration
    }

    /**
     * Builder for the [DistributionMode].
     */
    public fun distributionMode(configure: DistributionModeDSL.() -> Unit) {
        DistributionModeDSL().also { dsl ->
            dsl.configure()
            distributionMode = dsl.distributionMode
        }
    }

    public fun distributionMode(configure: Closure<DistributionModeDSL>) {
        val dsl = DistributionModeDSL()
        configure.delegate = dsl
        configure.call()
        distributionMode = dsl.distributionMode
    }

    /**
     * Builder for instances of [TargetPlatform].
     */
    public fun targetPlatforms(configure: TargetPlatformDsl.() -> Unit) {
        TargetPlatformDsl().also { dsl ->
            dsl.configure()
            targetPlatforms = dsl.targetPlatforms
        }
    }

    public fun targetPlatforms(configure: Closure<TargetPlatformDsl>) {
        val dsl = TargetPlatformDsl()
        configure.delegate = dsl
        configure.call()
        targetPlatforms = dsl.targetPlatforms
    }

    /**
     * Sets the name of the ZIP file.
     * Do not append the `.zip` file extension since it will be added during the build.
     *
     * Defaults to the [packageName] concatenated with the project version. E.g.
     * MyAwesomeKit-2.3.42-SNAPSHOT
     */
    public fun zipFileName(name: String) {
        zipFileName = ZipFileName.of(name)
    }

    /**
     * Sets the library type of the Swift package (.static or .dynamic).
     * If not set, no type will be specified in the Package.swift file.
     *
     * @param type The library type to use.
     */
    public fun libraryType(type: String) {
        libraryType = LibraryType.of(type)
    }
}

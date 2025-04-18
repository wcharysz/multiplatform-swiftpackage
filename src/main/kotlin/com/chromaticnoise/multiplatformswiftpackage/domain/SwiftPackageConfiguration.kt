package com.chromaticnoise.multiplatformswiftpackage.domain

import com.chromaticnoise.multiplatformswiftpackage.MultiplatformSwiftPackagePlugin
import org.gradle.api.Project

internal data class SwiftPackageConfiguration(
    private val project: Project,
    private val packageName: PackageName,
    private val toolVersion: SwiftToolVersion,
    private val platforms: String,
    private val distributionMode: DistributionMode,
    private val zipChecksum: String,
    private val zipFileName: ZipFileName,
    private val libraryType: LibraryType? = null
) {

    private val distributionUrl = when (distributionMode) {
        DistributionMode.Local -> null
        is DistributionMode.Remote -> distributionMode.url.appendPath(zipFileName.nameWithExtension)
    }

    // Determine if the zip file name is custom or default
    private val localPath = if (zipFileName.nameWithExtension.startsWith("${packageName.value}-${project.version}")) {
        "./${packageName.value}.xcframework"
    } else {
        // For custom zip file name, use it without the .zip extension
        "./${zipFileName.nameWithExtension}"
    }

    internal val templateProperties = mapOf(
        "toolsVersion" to toolVersion.name,
        "name" to packageName.value,
        "platforms" to platforms,
        "isLocal" to (distributionMode == DistributionMode.Local),
        "url" to distributionUrl?.value,
        "checksum" to zipChecksum.trim(),
        "hasLibraryType" to (libraryType != null),
        "libraryType" to libraryType?.value,
        "localPath" to localPath
    )

    internal companion object {
        internal const val FILE_NAME = "Package.swift"

        internal val templateFile =
            MultiplatformSwiftPackagePlugin::class.java.getResource("/templates/Package.swift.template")
    }
}

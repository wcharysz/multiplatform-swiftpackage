package com.chromaticnoise.multiplatformswiftpackage.task

import com.chromaticnoise.multiplatformswiftpackage.domain.OutputDirectory
import com.chromaticnoise.multiplatformswiftpackage.domain.ZipFileName
import org.gradle.api.Project
import java.io.File

internal fun zipFileChecksum(project: Project, outputDirectory: OutputDirectory, zipFileName: ZipFileName): String {
    val outputPath = outputDirectory.value
    return File(outputPath, zipFileName.nameWithExtension)
        .takeIf { it.exists() }
        ?.let { zipFile ->
            val execResult = project.providers.exec {
                workingDir = outputPath
                executable = "swift"
                args = listOf("package", "compute-checksum", zipFile.name)
            }
            execResult.result.get().assertNormalExitValue()
            execResult.standardOutput.asText.get()
        } ?: ""
}

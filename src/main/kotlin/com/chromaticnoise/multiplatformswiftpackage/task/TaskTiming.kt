package com.chromaticnoise.multiplatformswiftpackage.task

import org.gradle.api.Task

private const val TASK_START_NANOS_KEY = "multiplatformSwiftPackage.taskStartNanos"

internal fun Task.addTimingLogger(component: String, details: (() -> String)? = null) {
    doFirst {
        extensions.extraProperties[TASK_START_NANOS_KEY] = System.nanoTime()
    }
    doLast {
        val start = extensions.extraProperties[TASK_START_NANOS_KEY] as? Long ?: return@doLast
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        val suffix = details?.invoke()?.takeIf { it.isNotBlank() }?.let { " | $it" } ?: ""
        logger.lifecycle("[multiplatform-swift-package][timing] $component took ${elapsedMs}ms$suffix")
    }
}

internal inline fun <T> measureExecutionMs(block: () -> T): Pair<T, Long> {
    val start = System.nanoTime()
    val result = block()
    val elapsedMs = (System.nanoTime() - start) / 1_000_000
    return result to elapsedMs
}
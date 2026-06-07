// Copyright (c) 2026 Sergey S. Chernov (real.sergeych@gmail.com)
// SPDX-License-Identifier: MIT

package ews

internal actual fun readEwsResourceText(path: String): String {
    for (candidate in candidatePaths(path)) {
        val text = readNativeFileText(candidate)
        if (text != null) return text
    }

    throw IllegalArgumentException("Resource not found: $path")
}

private fun candidatePaths(path: String): List<String> = listOf(
    path,
    "ews/src/commonMain/resources/$path",
    "src/commonMain/resources/$path",
    "ews/build/processedResources/native/main/$path",
    "build/processedResources/native/main/$path",
    "ews/build/processedResources/linuxX64/main/$path",
    "build/processedResources/linuxX64/main/$path",
    "ews/build/processedResources/linuxArm64/main/$path",
    "build/processedResources/linuxArm64/main/$path",
    "ews/build/processedResources/macosX64/main/$path",
    "build/processedResources/macosX64/main/$path",
    "ews/build/processedResources/macosArm64/main/$path",
    "build/processedResources/macosArm64/main/$path",
    "ews/build/processedResources/iosX64/main/$path",
    "build/processedResources/iosX64/main/$path",
    "ews/build/processedResources/iosArm64/main/$path",
    "build/processedResources/iosArm64/main/$path",
    "ews/build/processedResources/iosSimulatorArm64/main/$path",
    "build/processedResources/iosSimulatorArm64/main/$path",
    "ews/build/processedResources/mingwX64/main/$path",
    "build/processedResources/mingwX64/main/$path",
)

internal expect fun readNativeFileText(path: String): String?

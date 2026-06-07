// Copyright (c) 2026 Sergey S. Chernov (real.sergeych@gmail.com)
// SPDX-License-Identifier: MIT

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package ews

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell

internal actual fun readEwsResourceText(path: String): String {
    for (candidate in candidatePaths(path)) {
        val text = readFileText(candidate)
        if (text != null) return text
    }

    throw IllegalArgumentException("Resource not found: $path")
}

private fun candidatePaths(path: String): List<String> = listOf(
    path,
    "ews/src/commonMain/resources/$path",
    "src/commonMain/resources/$path",
    "ews/build/processedResources/linuxX64/main/$path",
    "build/processedResources/linuxX64/main/$path",
)

private fun readFileText(path: String): String? {
    val file = fopen(path, "rb") ?: return null
    try {
        if (fseek(file, 0, SEEK_END) != 0) return null
        val size = ftell(file)
        if (size < 0) return null
        if (fseek(file, 0, SEEK_SET) != 0) return null

        val bytes = ByteArray(size.toInt())
        val read = bytes.usePinned { pinned ->
            fread(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file).toInt()
        }

        return bytes.decodeToString(endIndex = read)
    } finally {
        fclose(file)
    }
}

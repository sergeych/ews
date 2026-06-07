// Copyright (c) 2026 Sergey S. Chernov (real.sergeych@gmail.com)
// SPDX-License-Identifier: MIT

package ews

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js

internal actual fun readEwsResourceText(path: String): String {
    for (candidate in browserCandidates(path)) {
        val text = readBrowserResource(candidate)
        if (text != null) return text
    }

    throw IllegalArgumentException("Resource not found: $path")
}

private fun browserCandidates(path: String): List<String> = listOf(
    "/base/kotlin/$path",
    "/base/build/processedResources/wasmJs/main/$path",
    "/base/src/commonMain/resources/$path",
    "/base/ews/build/processedResources/wasmJs/main/$path",
    "/base/ews/src/commonMain/resources/$path",
    path,
)

private fun readBrowserResource(path: String): String? {
    val text = readBrowserResourceOrNull(path)
    return if (text.isNullOrEmpty()) null else text
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun readBrowserResourceOrNull(path: String): String? = js(
    """
        (() => {
            if (typeof XMLHttpRequest === 'undefined') return null;
            try {
                const request = new XMLHttpRequest();
                request.open('GET', path, false);
                request.send(null);
                if (request.status === 200 || request.status === 0) return request.responseText;
            } catch (e) {
            }
            return null;
        })()
    """
)

// Copyright (c) 2026 Sergey S. Chernov (real.sergeych@gmail.com)
// SPDX-License-Identifier: MIT

package ews

internal actual fun readEwsResourceText(path: String): String {
    val fs = js(
        """
            (function() {
                try {
                    if (typeof eval === 'function') return eval('require')('fs');
                } catch (e) {
                }
                return null;
            })()
            """
    )
    val cwd = js(
        """
            (function() {
                try {
                    if (typeof process !== 'undefined' && process.cwd) return process.cwd();
                } catch (e) {
                }
                return '';
            })()
            """
    ) as String
    val directory = js("(typeof __dirname !== 'undefined' ? __dirname : '')") as String
    val candidates = arrayOf(
        path,
        "$directory/$path",
        "$cwd/kotlin/$path",
        "$cwd/$path",
        "$cwd/ews/src/commonMain/resources/$path",
        "$cwd/src/commonMain/resources/$path",
        "$cwd/ews/build/processedResources/js/main/$path",
        "$cwd/build/processedResources/js/main/$path",
        "$directory/../../../../../processedResources/js/main/$path",
        "$directory/../../../../../../src/commonMain/resources/$path",
    )

    if (fs != null) {
        for (candidate in candidates) {
            if (fs.existsSync(candidate) as Boolean) {
                return fs.readFileSync(candidate, "utf8") as String
            }
        }
    }

    val browserCandidates = arrayOf(
        "/base/kotlin/$path",
        "/base/build/js/packages/ews-build-ews-test/kotlin/$path",
        "/base/build/processedResources/js/main/$path",
        "/base/src/commonMain/resources/$path",
        "/base/ews/build/processedResources/js/main/$path",
        "/base/ews/src/commonMain/resources/$path",
        path,
    )
    for (candidate in browserCandidates) {
        val text = readBrowserResource(candidate)
        if (text != null) return text
    }

    throw IllegalArgumentException(
        "Resource not found: $path; tried ${(candidates + browserCandidates).joinToString()}"
    )
}

private fun readBrowserResource(path: String): String? {
    val hasXmlHttpRequest = js("typeof XMLHttpRequest !== 'undefined'") as Boolean
    if (!hasXmlHttpRequest) return null

    return try {
        val request = js("new XMLHttpRequest()")
        request.open("GET", path, false)
        request.send(null)
        val status = request.status as Int
        if (status == 200 || status == 0) {
            request.responseText as String
        } else {
            null
        }
    } catch (_: Throwable) {
        null
    }
}

// Copyright (c) 2026 Sergey S. Chernov (real.sergeych@gmail.com)
// SPDX-License-Identifier: MIT

package ews

internal actual fun readNativeFileText(path: String): String? = readPosixFileText(path)

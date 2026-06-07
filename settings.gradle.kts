// Copyright (c) 2026 Sergey S. Chernov (real.sergeych@gmail.com)
// SPDX-License-Identifier: MIT

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "ews"

include(":ews")

// Copyright (c) 2026 Sergey S. Chernov (real.sergeych@gmail.com)
// SPDX-License-Identifier: MIT

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(17)

    jvm()

    js {
        browser()
        nodejs()
    }

    linuxX64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

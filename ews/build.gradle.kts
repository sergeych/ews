import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// Copyright (c) 2026 Sergey S. Chernov (real.sergeych@gmail.com)
// SPDX-License-Identifier: MIT

plugins {
    kotlin("multiplatform")
    `maven-publish`
}

group = "net.sergeych"
version = "0.1.1-SNAPSHOT"

kotlin {
    jvmToolchain(17)

    jvm()

    js {
        browser()
        nodejs()
    }

    linuxX64()
    linuxX64()
    linuxArm64()

    macosX64()
    macosArm64()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    mingwX64()
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

publishing {
    val mavenToken by lazy {
        File("${System.getProperty("user.home")}/.gitea_token").readText()
    }
    repositories {
        maven {
            credentials(HttpHeaderCredentials::class) {
                name = "Authorization"
                value = mavenToken
            }
            url = uri("https://gitea.sergeych.net/api/packages/SergeychWorks/maven")
            authentication {
                create("Authorization", HttpHeaderAuthentication::class)
            }
        }
    }
}

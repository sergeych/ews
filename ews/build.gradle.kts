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

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidMultiplatformLibrary)
}
kotlin {
    android {
        namespace = "com.portal.appinspector"
        compileSdk = 36
        minSdk = 24

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":lib"))
            implementation(libs.compose.foundation)
            implementation(libs.compose.runtime)
            implementation("org.jetbrains.compose.ui:ui-tooling-preview:1.11.1")
            implementation("io.viascom.nanoid:nanoid:2.0.1")
            implementation(libs.compose.ui)
            implementation(libs.docklayout)
            implementation(libs.json.tree)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.wrappers.browser)
        }
    }
}

dependencies {
    androidRuntimeClasspath("org.jetbrains.compose.ui:ui-tooling:1.11.1")
}
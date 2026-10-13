package com.radustavila.myblesense.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

internal fun VersionCatalog.library(alias: String) = findLibrary(alias).get()

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal val Project.javaVersion: JavaVersion
    get() = JavaVersion.toVersion(libs.findVersion("jvmToolchain").get().requiredVersion)

/** Shared Android + Kotlin configuration applied to every Android module. */
internal fun Project.configureAndroidKotlin(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = libs.findVersion("compileSdk").get().requiredVersion.toInt()

        defaultConfig.minSdk = libs.findVersion("minSdk").get().requiredVersion.toInt()

        compileOptions.sourceCompatibility = javaVersion
        compileOptions.targetCompatibility = javaVersion

        testOptions.unitTests.isReturnDefaultValues = true
    }

    // AGP 9 applies the Kotlin plugin itself; the project extension is configured, not applied.
    extensions.findByType(KotlinAndroidProjectExtension::class.java)?.compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
        configureFreeCompilerArgs()
    }
}

internal fun org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions.configureFreeCompilerArgs() {
    freeCompilerArgs.addAll(
        // Coroutines Flow APIs used by the BLE layer are still marked experimental.
        "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        "-opt-in=kotlin.time.ExperimentalTime",
    )
}

internal fun Project.configureUnitTests() {
    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
        useJUnit()
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}

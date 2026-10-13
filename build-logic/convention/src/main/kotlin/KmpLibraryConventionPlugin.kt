import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.radustavila.myblesense.buildlogic.configureFreeCompilerArgs
import com.radustavila.myblesense.buildlogic.configureUnitTests
import com.radustavila.myblesense.buildlogic.javaVersion
import com.radustavila.myblesense.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Applied by every shared module: Kotlin Multiplatform with an Android target (JVM host tests
 * enabled) and the iOS targets. The Android namespace is derived from the project path, e.g.
 * `:feature:device-details` -> `com.radustavila.myblesense.feature.devicedetails`.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")

        extensions.configure<KotlinMultiplatformExtension> {
            (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                namespace = "com.radustavila.myblesense" + path.replace(":", ".").replace("-", "")
                compileSdk = libs.findVersion("compileSdk").get().requiredVersion.toInt()
                minSdk = libs.findVersion("minSdk").get().requiredVersion.toInt()

                withHostTest { isReturnDefaultValues = true }

                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
                }
            }

            iosArm64()
            iosSimulatorArm64()

            compilerOptions {
                configureFreeCompilerArgs()
            }

            sourceSets.commonTest.dependencies {
                implementation(kotlin("test"))
                implementation(libs.findLibrary("kotlinx-coroutines-test").get())
                implementation(libs.findLibrary("turbine").get())
            }
        }
        configureUnitTests()

        // Keeps `./gradlew test` meaning "all JVM unit tests", as before the KMP migration.
        tasks.register("test") {
            group = "verification"
            description = "Runs the unit tests of this module on the JVM (Android host tests)."
            dependsOn("testAndroidHostTest")
        }
        Unit
    }
}

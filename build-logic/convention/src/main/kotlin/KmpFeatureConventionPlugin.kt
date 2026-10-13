import com.radustavila.myblesense.buildlogic.library
import com.radustavila.myblesense.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Applied by every `:feature:*` module: KMP library + Compose Multiplatform + the presentation
 * dependencies (ViewModel, navigation, Koin) every feature needs.
 */
class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("myblesense.kmp.library")
        pluginManager.apply("myblesense.kmp.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.commonMain.dependencies {
                implementation(project(":domain"))
                implementation(project(":core:common"))
                implementation(project(":core:designsystem"))

                implementation(libs.library("lifecycle-runtime-compose"))
                implementation(libs.library("lifecycle-viewmodel-compose"))
                implementation(libs.library("navigation-compose"))
                implementation(libs.library("kotlinx-serialization-json"))
                implementation(libs.library("koin-core"))
                implementation(libs.library("koin-compose-viewmodel"))
            }
            sourceSets.commonTest.dependencies {
                implementation(project(":domain:testing"))
            }
        }
    }
}

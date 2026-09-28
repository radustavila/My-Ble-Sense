import com.radustavila.myblesense.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * Applied by every `:feature:*` module: Android library + Compose + Hilt + the presentation
 * dependencies (ViewModel, navigation) every feature needs.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("myblesense.android.library")
        pluginManager.apply("myblesense.android.compose")
        pluginManager.apply("myblesense.hilt")

        dependencies {
            add("implementation", project(":domain"))
            add("implementation", project(":core:common"))
            add("implementation", project(":core:designsystem"))

            add("implementation", libs.library("androidx-core-ktx"))
            add("implementation", libs.library("androidx-lifecycle-runtime-ktx"))
            add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.library("androidx-hilt-navigation-compose"))
            add("implementation", libs.library("androidx-navigation-compose"))
            add("implementation", libs.library("kotlinx-coroutines-android"))

            add("testImplementation", libs.library("junit"))
            add("testImplementation", libs.library("truth"))
            add("testImplementation", libs.library("turbine"))
            add("testImplementation", libs.library("kotlinx-coroutines-test"))

            add("androidTestImplementation", libs.library("androidx-test-ext-junit"))
            add("androidTestImplementation", libs.library("truth"))
            add("androidTestImplementation", libs.library("androidx-compose-ui-test-junit4"))
            add("debugImplementation", libs.library("androidx-compose-ui-test-manifest"))
        }
    }
}

import com.android.build.api.dsl.ApplicationExtension
import com.radustavila.bleexplorer.buildlogic.configureAndroidKotlin
import com.radustavila.bleexplorer.buildlogic.configureUnitTests
import com.radustavila.bleexplorer.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            configureAndroidKotlin(this)
            defaultConfig.targetSdk = libs.findVersion("targetSdk").get().requiredVersion.toInt()
        }
        configureUnitTests()
    }
}

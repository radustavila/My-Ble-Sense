import com.radustavila.bleexplorer.buildlogic.configureKotlinJvm
import com.radustavila.bleexplorer.buildlogic.configureUnitTests
import org.gradle.api.Plugin
import org.gradle.api.Project

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        configureKotlinJvm()
        configureUnitTests()
    }
}

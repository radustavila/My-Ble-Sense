plugins {
    alias(libs.plugins.bleexplorer.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    namespace = "com.radustavila.bleexplorer.feature.scanner"
}

dependencies {
    implementation(projects.core.bluetooth)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(testFixtures(projects.domain))
}

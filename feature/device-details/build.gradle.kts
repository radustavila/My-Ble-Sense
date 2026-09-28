plugins {
    alias(libs.plugins.bleexplorer.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    namespace = "com.radustavila.bleexplorer.feature.devicedetails"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(testFixtures(projects.domain))
}

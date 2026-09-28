plugins {
    alias(libs.plugins.myblesense.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    namespace = "com.radustavila.myblesense.feature.scanner"
}

dependencies {
    implementation(projects.core.bluetooth)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(testFixtures(projects.domain))
}

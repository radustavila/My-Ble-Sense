plugins {
    alias(libs.plugins.myblesense.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    namespace = "com.radustavila.myblesense.feature.gattinspector"
}

dependencies {
    // Only for the Bluetooth SIG UUID names; the GATT stack itself is reached through use cases.
    implementation(projects.core.bluetooth)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(testFixtures(projects.domain))
}

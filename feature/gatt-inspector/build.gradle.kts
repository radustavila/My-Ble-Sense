plugins {
    alias(libs.plugins.bleexplorer.android.feature)
    alias(libs.plugins.kotlin.serialization)
}

android {
    defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    namespace = "com.radustavila.bleexplorer.feature.gattinspector"
}

dependencies {
    // Only for the Bluetooth SIG UUID names; the GATT stack itself is reached through use cases.
    implementation(projects.core.bluetooth)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(testFixtures(projects.domain))
}

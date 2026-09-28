plugins {
    alias(libs.plugins.bleexplorer.android.library)
    alias(libs.plugins.bleexplorer.hilt)
}

android {
    namespace = "com.radustavila.bleexplorer.core.bluetooth"
}

dependencies {
    api(projects.domain)
    implementation(projects.core.common)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(testFixtures(projects.domain))
}

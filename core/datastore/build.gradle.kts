plugins {
    alias(libs.plugins.bleexplorer.android.library)
    alias(libs.plugins.bleexplorer.hilt)
}

android {
    namespace = "com.radustavila.bleexplorer.core.datastore"
}

dependencies {
    api(projects.domain)
    implementation(projects.core.common)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

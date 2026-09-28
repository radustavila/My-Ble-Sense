plugins {
    alias(libs.plugins.myblesense.android.library)
    alias(libs.plugins.myblesense.hilt)
}

android {
    namespace = "com.radustavila.myblesense.core.datastore"
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

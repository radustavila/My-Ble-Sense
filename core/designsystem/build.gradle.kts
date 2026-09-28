plugins {
    alias(libs.plugins.myblesense.android.library)
    alias(libs.plugins.myblesense.android.compose)
}

android {
    namespace = "com.radustavila.myblesense.core.designsystem"
}

dependencies {
    // Error copy lives here so every feature shows the same message for the same domain error.
    api(projects.domain)
    implementation(libs.androidx.core.ktx)
    api(libs.androidx.compose.material.icons.extended)
}

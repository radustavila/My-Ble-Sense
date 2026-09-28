plugins {
    alias(libs.plugins.bleexplorer.android.library)
    alias(libs.plugins.bleexplorer.hilt)
}

android {
    namespace = "com.radustavila.bleexplorer.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

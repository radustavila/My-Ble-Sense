plugins {
    alias(libs.plugins.myblesense.android.library)
    alias(libs.plugins.myblesense.hilt)
}

android {
    namespace = "com.radustavila.myblesense.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

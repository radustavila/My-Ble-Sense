plugins {
    alias(libs.plugins.myblesense.android.application)
    alias(libs.plugins.myblesense.android.compose)
    alias(libs.plugins.myblesense.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.radustavila.myblesense"

    // Used to keep verbose BLE logging out of release builds.
    buildFeatures.buildConfig = true

    defaultConfig {
        applicationId = "com.radustavila.myblesense"
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(projects.core.bluetooth)
    implementation(projects.core.common)
    implementation(projects.core.datastore)
    implementation(projects.core.designsystem)
    implementation(projects.domain)
    implementation(projects.feature.scanner)
    implementation(projects.feature.deviceDetails)
    implementation(projects.feature.gattInspector)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}

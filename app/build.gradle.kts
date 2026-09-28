plugins {
    alias(libs.plugins.bleexplorer.android.application)
    alias(libs.plugins.bleexplorer.android.compose)
    alias(libs.plugins.bleexplorer.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.radustavila.bleexplorer"

    // Used to keep verbose BLE logging out of release builds.
    buildFeatures.buildConfig = true

    defaultConfig {
        applicationId = "com.radustavila.bleexplorer"
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

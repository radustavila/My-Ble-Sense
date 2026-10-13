plugins {
    alias(libs.plugins.myblesense.android.application)
    alias(libs.plugins.myblesense.android.compose)
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
    // The whole app (navigation graph, DI graph, screens) is the shared KMP module; this module
    // is only the Android shell around it.
    implementation(projects.shared.app)
    implementation(projects.core.common)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
}

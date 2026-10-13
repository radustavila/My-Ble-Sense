plugins {
    alias(libs.plugins.myblesense.kmp.feature)
}

kotlin {
    android {
        // Compose Multiplatform's resource tasks for the device tests need Android resources on.
        androidResources { enable = true }

        // Compose UI tests; they need an API <= 36 device, see CLAUDE.md.
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.truth)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.ui.test.junit4)
            implementation(libs.androidx.compose.ui.test.manifest)
        }
    }
}

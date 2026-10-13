plugins {
    alias(libs.plugins.myblesense.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.domain)
            implementation(projects.core.common)
            implementation(libs.androidx.datastore.preferences.core)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            implementation(projects.domain.testing)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
        }
    }
}

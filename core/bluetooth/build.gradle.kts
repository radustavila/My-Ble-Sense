plugins {
    alias(libs.plugins.myblesense.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.domain)
            implementation(projects.core.common)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            implementation(projects.domain.testing)
        }
    }
}

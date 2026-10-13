plugins {
    alias(libs.plugins.myblesense.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            api(libs.kotlinx.coroutines.android)
        }
        commonTest.dependencies {
            implementation(projects.domain.testing)
        }
    }
}

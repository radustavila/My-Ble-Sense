plugins {
    alias(libs.plugins.myblesense.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(projects.domain.testing)
        }
    }
}

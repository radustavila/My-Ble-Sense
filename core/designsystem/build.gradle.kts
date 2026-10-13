plugins {
    alias(libs.plugins.myblesense.kmp.library)
    alias(libs.plugins.myblesense.kmp.compose)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Error copy lives here so every feature shows the same message for the same domain error.
            api(projects.domain)
            api(libs.compose.material.icons.extended)
        }
    }
}

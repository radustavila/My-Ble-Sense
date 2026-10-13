plugins {
    alias(libs.plugins.myblesense.kmp.library)
    alias(libs.plugins.myblesense.kmp.compose)
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.domain)
            implementation(projects.core.bluetooth)
            implementation(projects.core.common)
            implementation(projects.core.datastore)
            implementation(projects.core.designsystem)
            implementation(projects.feature.scanner)
            implementation(projects.feature.deviceDetails)
            implementation(projects.feature.gattInspector)

            implementation(libs.navigation.compose)
            implementation(libs.koin.core)
        }
    }
}

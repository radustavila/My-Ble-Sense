plugins {
    `kotlin-dsl`
}

group = "com.radustavila.myblesense.buildlogic"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jvmToolchain.get()))
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "myblesense.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidCompose") {
            id = "myblesense.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("kmpLibrary") {
            id = "myblesense.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = "myblesense.kmp.compose"
            implementationClass = "KmpComposeConventionPlugin"
        }
        register("kmpFeature") {
            id = "myblesense.kmp.feature"
            implementationClass = "KmpFeatureConventionPlugin"
        }
    }
}

plugins {
    alias(libs.plugins.myblesense.kmp.library)
}

// Shared test infrastructure (fakes, assertions, the Main dispatcher helper), consumed by every
// module's `commonTest`. KMP has no `testFixtures`, hence a module of its own.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.domain)
            api(kotlin("test"))
            api(libs.kotlinx.coroutines.test)
        }
    }
}

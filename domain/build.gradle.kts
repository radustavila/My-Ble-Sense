plugins {
    alias(libs.plugins.bleexplorer.jvm.library)
    `java-test-fixtures`
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    testFixturesApi(libs.kotlinx.coroutines.core)
    // Shared test infrastructure (fakes, the Main dispatcher rule) is consumed by every module.
    testFixturesApi(libs.junit)
    testFixturesApi(libs.kotlinx.coroutines.test)
}

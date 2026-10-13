package com.radustavila.myblesense.domain.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Replaces `Dispatchers.Main` (used by `viewModelScope`) with a test dispatcher.
 *
 * Tests run on [testScheduler] so that the virtual clock of the test and of the ViewModel's
 * coroutines is the same one — otherwise timeouts could never be advanced.
 *
 * Multiplatform tests have no JUnit rules, so a suite calls [install] from a `@BeforeTest`
 * function and [reset] from an `@AfterTest` one.
 */
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) {

    val testScheduler get() = testDispatcher.scheduler

    fun install() = Dispatchers.setMain(testDispatcher)

    fun reset() = Dispatchers.resetMain()
}

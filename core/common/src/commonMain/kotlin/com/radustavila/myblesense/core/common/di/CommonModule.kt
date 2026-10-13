package com.radustavila.myblesense.core.common.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Qualifier of the dispatcher for blocking I/O (`Dispatchers.IO` on both platforms). */
val IoDispatcher = named("IoDispatcher")

val DefaultDispatcher = named("DefaultDispatcher")

/**
 * Qualifier of the application scope: the scope for the rare piece of work that must outlive the
 * component that started it — closing a GATT connection when the screen that owns it is being
 * destroyed, and remembering the known device as connections succeed — and nothing else.
 *
 * It is injected rather than reached for as a global, so tests replace it like any other
 * dependency; `GlobalScope` would give neither property.
 */
val ApplicationScope = named("ApplicationScope")

/** Injecting dispatchers keeps every coroutine boundary swappable in tests. */
val commonModule = module {
    single<CoroutineDispatcher>(IoDispatcher) { platformIoDispatcher }
    single<CoroutineDispatcher>(DefaultDispatcher) { Dispatchers.Default }
    single<CoroutineScope>(ApplicationScope) {
        CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>(IoDispatcher))
    }
}

/** `Dispatchers.IO` is not part of the common coroutines API. */
internal expect val platformIoDispatcher: CoroutineDispatcher

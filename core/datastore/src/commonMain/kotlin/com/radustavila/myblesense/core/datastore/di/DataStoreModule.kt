package com.radustavila.myblesense.core.datastore.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.radustavila.myblesense.core.common.di.IoDispatcher
import com.radustavila.myblesense.core.datastore.DataStoreKnownDeviceRepository
import com.radustavila.myblesense.domain.repository.KnownDeviceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath
import org.koin.core.module.Module
import org.koin.core.scope.Scope
import org.koin.dsl.module

/**
 * Provides the DataStore file and binds the domain repository to it. A DataStore must be a
 * singleton per file: two instances on the same file would corrupt it.
 */
val dataStoreModule: Module = module {
    single<KnownDeviceRepository> {
        val path = knownDeviceDataStorePath()
        DataStoreKnownDeviceRepository(
            PreferenceDataStoreFactory.createWithPath(
                scope = CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>(IoDispatcher)),
                produceFile = { path.toPath() },
            ),
        )
    }
}

/** Absolute path of the known-device preferences file in the app's private storage. */
internal expect fun Scope.knownDeviceDataStorePath(): String

internal const val KNOWN_DEVICE_FILE = "known_device.preferences_pb"

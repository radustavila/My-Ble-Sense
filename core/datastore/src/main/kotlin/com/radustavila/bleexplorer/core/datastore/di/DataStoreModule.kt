package com.radustavila.bleexplorer.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.radustavila.bleexplorer.core.common.di.IoDispatcher
import com.radustavila.bleexplorer.core.datastore.DataStoreKnownDeviceRepository
import com.radustavila.bleexplorer.core.datastore.KnownDevicePreferences
import com.radustavila.bleexplorer.domain.repository.KnownDeviceRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Provides the DataStore file and binds the domain repository to it. A DataStore must be a
 * singleton per file: two instances on the same file would corrupt it.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreModule {

    @Binds
    @Singleton
    abstract fun bindKnownDeviceRepository(
        impl: DataStoreKnownDeviceRepository,
    ): KnownDeviceRepository

    companion object {
        @Provides
        @Singleton
        @KnownDevicePreferences
        fun provideKnownDeviceDataStore(
            @ApplicationContext context: Context,
            @IoDispatcher ioDispatcher: CoroutineDispatcher,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + ioDispatcher),
            produceFile = { context.preferencesDataStoreFile(KNOWN_DEVICE_FILE) },
        )

        private const val KNOWN_DEVICE_FILE = "known_device"
    }
}

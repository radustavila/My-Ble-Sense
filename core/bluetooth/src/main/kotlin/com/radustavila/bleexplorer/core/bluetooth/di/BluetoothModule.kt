package com.radustavila.bleexplorer.core.bluetooth.di

import com.radustavila.bleexplorer.core.bluetooth.gatt.AndroidBleGattClient
import com.radustavila.bleexplorer.core.bluetooth.repository.AndroidBleGattRepository
import com.radustavila.bleexplorer.core.bluetooth.repository.AndroidBleScanRepository
import com.radustavila.bleexplorer.core.bluetooth.scanner.AndroidBleScanner
import com.radustavila.bleexplorer.core.bluetooth.state.AndroidBluetoothStateMonitor
import com.radustavila.bleexplorer.domain.repository.BleGattClient
import com.radustavila.bleexplorer.domain.repository.BleGattRepository
import com.radustavila.bleexplorer.domain.repository.BleScanRepository
import com.radustavila.bleexplorer.domain.repository.BleScanner
import com.radustavila.bleexplorer.domain.repository.BluetoothStateMonitor
import com.radustavila.bleexplorer.domain.session.InMemoryScanSessionStore
import com.radustavila.bleexplorer.domain.session.ScanSessionStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the domain abstractions to their Android implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BluetoothModule {

    @Binds
    @Singleton
    abstract fun bindBleScanner(impl: AndroidBleScanner): BleScanner

    @Binds
    @Singleton
    abstract fun bindBluetoothStateMonitor(
        impl: AndroidBluetoothStateMonitor,
    ): BluetoothStateMonitor

    @Binds
    @Singleton
    abstract fun bindBleScanRepository(impl: AndroidBleScanRepository): BleScanRepository

    @Binds
    @Singleton
    abstract fun bindBleGattClient(impl: AndroidBleGattClient): BleGattClient

    @Binds
    @Singleton
    abstract fun bindBleGattRepository(impl: AndroidBleGattRepository): BleGattRepository

    @Binds
    @Singleton
    abstract fun bindScanSessionStore(impl: InMemoryScanSessionStore): ScanSessionStore
}

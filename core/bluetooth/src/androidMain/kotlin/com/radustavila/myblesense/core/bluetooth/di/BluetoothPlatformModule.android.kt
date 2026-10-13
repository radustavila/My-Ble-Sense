package com.radustavila.myblesense.core.bluetooth.di

import com.radustavila.myblesense.core.bluetooth.gatt.AndroidBleGattClient
import com.radustavila.myblesense.core.bluetooth.mapper.ScanResultMapper
import com.radustavila.myblesense.core.bluetooth.permission.AndroidBlePermissionChecker
import com.radustavila.myblesense.core.bluetooth.permission.BlePermissionChecker
import com.radustavila.myblesense.core.bluetooth.scanner.AndroidBleScanner
import com.radustavila.myblesense.core.bluetooth.state.AndroidBluetoothStateMonitor
import com.radustavila.myblesense.core.common.di.IoDispatcher
import com.radustavila.myblesense.domain.repository.BleGattClient
import com.radustavila.myblesense.domain.repository.BleScanner
import com.radustavila.myblesense.domain.repository.BluetoothStateMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

internal actual val bluetoothPlatformModule: Module = module {
    single { AndroidBluetoothStateMonitor(androidContext()) } bind BluetoothStateMonitor::class
    single<BlePermissionChecker> { AndroidBlePermissionChecker(androidContext()) }
    single { ScanResultMapper() }
    single<BleScanner> { AndroidBleScanner(get(), get(), get()) }
    single<BleGattClient> { AndroidBleGattClient(androidContext(), get(), get(), get(IoDispatcher)) }
}

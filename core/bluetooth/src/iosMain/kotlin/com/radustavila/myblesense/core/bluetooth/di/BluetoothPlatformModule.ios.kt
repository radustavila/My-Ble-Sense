package com.radustavila.myblesense.core.bluetooth.di

import com.radustavila.myblesense.core.bluetooth.central.IosCentral
import com.radustavila.myblesense.core.bluetooth.gatt.IosBleGattClient
import com.radustavila.myblesense.core.bluetooth.permission.BlePermissionChecker
import com.radustavila.myblesense.core.bluetooth.permission.IosBlePermissionChecker
import com.radustavila.myblesense.core.bluetooth.scanner.IosBleScanner
import com.radustavila.myblesense.core.bluetooth.state.IosBluetoothStateMonitor
import com.radustavila.myblesense.core.common.di.IoDispatcher
import com.radustavila.myblesense.domain.repository.BleGattClient
import com.radustavila.myblesense.domain.repository.BleScanner
import com.radustavila.myblesense.domain.repository.BluetoothStateMonitor
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val bluetoothPlatformModule: Module = module {
    single { IosCentral() }
    single<BluetoothStateMonitor> { IosBluetoothStateMonitor(get()) }
    single<BlePermissionChecker> { IosBlePermissionChecker() }
    single<BleScanner> { IosBleScanner(get(), get()) }
    single<BleGattClient> { IosBleGattClient(get(), get(), get(), get(IoDispatcher)) }
}

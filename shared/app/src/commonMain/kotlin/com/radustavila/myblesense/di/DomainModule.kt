package com.radustavila.myblesense.di

import com.radustavila.myblesense.domain.usecase.ClearScanSessionUseCase
import com.radustavila.myblesense.domain.usecase.ConnectToDeviceUseCase
import com.radustavila.myblesense.domain.usecase.DisconnectFromDeviceUseCase
import com.radustavila.myblesense.domain.usecase.ForgetKnownDeviceUseCase
import com.radustavila.myblesense.domain.usecase.GetBleCapabilityUseCase
import com.radustavila.myblesense.domain.usecase.ObserveBluetoothStateUseCase
import com.radustavila.myblesense.domain.usecase.ObserveCharacteristicValuesUseCase
import com.radustavila.myblesense.domain.usecase.ObserveGattConnectionUseCase
import com.radustavila.myblesense.domain.usecase.ObserveGattErrorsUseCase
import com.radustavila.myblesense.domain.usecase.ObserveGattServicesUseCase
import com.radustavila.myblesense.domain.usecase.ObserveKnownDeviceUseCase
import com.radustavila.myblesense.domain.usecase.ObserveNotifyingCharacteristicsUseCase
import com.radustavila.myblesense.domain.usecase.ObservePocEventsUseCase
import com.radustavila.myblesense.domain.usecase.ObservePocHardwareEventsUseCase
import com.radustavila.myblesense.domain.usecase.ObservePocServiceUseCase
import com.radustavila.myblesense.domain.usecase.ObserveScannedDeviceUseCase
import com.radustavila.myblesense.domain.usecase.ReadCharacteristicUseCase
import com.radustavila.myblesense.domain.usecase.ReadPocStatusUseCase
import com.radustavila.myblesense.domain.usecase.ReconnectToDeviceUseCase
import com.radustavila.myblesense.domain.usecase.SendPocCommandUseCase
import com.radustavila.myblesense.domain.usecase.SetCharacteristicNotificationsUseCase
import com.radustavila.myblesense.domain.usecase.SetPocEventsNotificationsUseCase
import com.radustavila.myblesense.domain.usecase.SortDevicesUseCase
import com.radustavila.myblesense.domain.usecase.StartBleScanUseCase
import com.radustavila.myblesense.domain.usecase.TrackKnownDeviceUseCase
import com.radustavila.myblesense.domain.usecase.WriteCharacteristicUseCase
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * The use cases. They are wired here rather than in `:domain` so the domain layer stays free of
 * any DI framework: its classes only have plain constructors.
 */
val domainModule: Module = module {
    factoryOf(::ClearScanSessionUseCase)
    factoryOf(::ConnectToDeviceUseCase)
    factoryOf(::DisconnectFromDeviceUseCase)
    factoryOf(::ForgetKnownDeviceUseCase)
    factoryOf(::GetBleCapabilityUseCase)
    factoryOf(::ObserveBluetoothStateUseCase)
    factoryOf(::ObserveCharacteristicValuesUseCase)
    factoryOf(::ObserveGattConnectionUseCase)
    factoryOf(::ObserveGattErrorsUseCase)
    factoryOf(::ObserveGattServicesUseCase)
    factoryOf(::ObserveKnownDeviceUseCase)
    factoryOf(::ObserveNotifyingCharacteristicsUseCase)
    factoryOf(::ObservePocEventsUseCase)
    factoryOf(::ObservePocHardwareEventsUseCase)
    factoryOf(::ObservePocServiceUseCase)
    factoryOf(::ObserveScannedDeviceUseCase)
    factoryOf(::ReadCharacteristicUseCase)
    factoryOf(::ReadPocStatusUseCase)
    factoryOf(::ReconnectToDeviceUseCase)
    factoryOf(::SendPocCommandUseCase)
    factoryOf(::SetCharacteristicNotificationsUseCase)
    factoryOf(::SetPocEventsNotificationsUseCase)
    factoryOf(::SortDevicesUseCase)
    factoryOf(::StartBleScanUseCase)
    factoryOf(::TrackKnownDeviceUseCase)
    factoryOf(::WriteCharacteristicUseCase)
}

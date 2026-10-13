package com.radustavila.myblesense.domain.usecase

import com.radustavila.myblesense.domain.model.BleDevice
import com.radustavila.myblesense.domain.model.DeviceSortOrder
import com.radustavila.myblesense.domain.model.comparator

/** Keeps ordering rules out of the ViewModel and out of Compose. */
class SortDevicesUseCase() {
    operator fun invoke(devices: List<BleDevice>, order: DeviceSortOrder): List<BleDevice> =
        devices.sortedWith(order.comparator())
}

package com.radustavila.bleexplorer.domain.usecase

import com.radustavila.bleexplorer.domain.model.BleDevice
import com.radustavila.bleexplorer.domain.model.DeviceSortOrder
import com.radustavila.bleexplorer.domain.model.comparator
import javax.inject.Inject

/** Keeps ordering rules out of the ViewModel and out of Compose. */
class SortDevicesUseCase @Inject constructor() {
    operator fun invoke(devices: List<BleDevice>, order: DeviceSortOrder): List<BleDevice> =
        devices.sortedWith(order.comparator())
}

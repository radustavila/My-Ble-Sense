package com.radustavila.bleexplorer.domain.usecase

import com.radustavila.bleexplorer.domain.model.BleDevice
import com.radustavila.bleexplorer.domain.model.BleScanSettings
import com.radustavila.bleexplorer.domain.model.DiscoveredDevices
import com.radustavila.bleexplorer.domain.repository.BleScanRepository
import com.radustavila.bleexplorer.domain.session.ScanSessionStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.time.Duration
import javax.inject.Inject

/**
 * Starts a bounded BLE scan and emits the de-duplicated device list after every advertisement.
 *
 * The returned flow:
 *  - is cold: the platform scan starts on collection and stops when collection is cancelled;
 *  - **completes on its own** once [BleScanSettings.duration] elapses, which is how the timeout
 *    is implemented — no timer is left running and no callback is leaked;
 *  - fails with [com.radustavila.bleexplorer.domain.model.BleException] on any BLE error.
 */
class StartBleScanUseCase @Inject constructor(
    private val repository: BleScanRepository,
    private val sessionStore: ScanSessionStore,
) {
    operator fun invoke(settings: BleScanSettings = BleScanSettings()): Flow<List<BleDevice>> =
        repository.scan(settings)
            .runningFold(DiscoveredDevices.EMPTY) { discovered, device -> discovered.upsert(device) }
            .drop(1) // the seed is only the accumulator's starting point, not a real emission
            .map { it.devices }
            .onEach(sessionStore::publish)
            .takeFor(settings.duration)
}

/**
 * Emits the upstream values for at most [duration], then completes.
 *
 * `withTimeout` cannot be used around `emit` (it would break the flow context preservation
 * invariant), hence the channelFlow with a child collector job.
 */
internal fun <T> Flow<T>.takeFor(duration: Duration): Flow<T> = channelFlow {
    val collector = launch { collect { send(it) } }
    delay(duration)
    // Cancelling the collector stops the platform scan; returning closes the channel, which
    // completes the flow for the collector downstream.
    collector.cancel()
}

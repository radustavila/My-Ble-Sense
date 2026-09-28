package com.radustavila.myblesense.feature.gattinspector

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.myblesense.core.common.format.parseHexOrNull
import com.radustavila.myblesense.core.designsystem.text.toUserMessage
import com.radustavila.myblesense.domain.model.GattCharacteristicId
import com.radustavila.myblesense.domain.model.GattCharacteristicValue
import com.radustavila.myblesense.domain.model.GattConnection
import com.radustavila.myblesense.domain.model.GattConnectionState
import com.radustavila.myblesense.domain.model.GattValueSource
import com.radustavila.myblesense.domain.model.toBleError
import com.radustavila.myblesense.domain.usecase.DisconnectFromDeviceUseCase
import com.radustavila.myblesense.domain.usecase.ObserveCharacteristicValuesUseCase
import com.radustavila.myblesense.domain.usecase.ObserveGattConnectionUseCase
import com.radustavila.myblesense.domain.usecase.ObserveGattErrorsUseCase
import com.radustavila.myblesense.domain.usecase.ObserveGattServicesUseCase
import com.radustavila.myblesense.domain.usecase.ObserveNotifyingCharacteristicsUseCase
import com.radustavila.myblesense.domain.usecase.ObserveScannedDeviceUseCase
import com.radustavila.myblesense.domain.usecase.ReadCharacteristicUseCase
import com.radustavila.myblesense.domain.usecase.SetCharacteristicNotificationsUseCase
import com.radustavila.myblesense.domain.usecase.WriteCharacteristicUseCase
import com.radustavila.myblesense.feature.gattinspector.navigation.gattInspectorDeviceId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the GATT inspector screen.
 *
 * The connection itself is *not* owned here: it lives in the app-scoped GATT client and is
 * started from the device details screen, which is what lets the user navigate between the two
 * screens (and rotate the phone) without dropping the link or opening a second one. This
 * ViewModel only observes it and issues operations, so its own recreation can never produce a
 * duplicate connection or a duplicate callback.
 */
@HiltViewModel
class GattInspectorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeScannedDevice: ObserveScannedDeviceUseCase,
    observeConnection: ObserveGattConnectionUseCase,
    observeServices: ObserveGattServicesUseCase,
    observeNotifying: ObserveNotifyingCharacteristicsUseCase,
    observeCharacteristicValues: ObserveCharacteristicValuesUseCase,
    observeGattErrors: ObserveGattErrorsUseCase,
    private val readCharacteristic: ReadCharacteristicUseCase,
    private val writeCharacteristic: WriteCharacteristicUseCase,
    private val setCharacteristicNotifications: SetCharacteristicNotificationsUseCase,
    private val disconnectFromDevice: DisconnectFromDeviceUseCase,
) : ViewModel() {

    private val deviceId: String = savedStateHandle.gattInspectorDeviceId()

    private val state = MutableStateFlow(InspectorState(deviceId = deviceId))

    val uiState: StateFlow<GattInspectorUiState> = state
        .map(InspectorState::toUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = state.value.toUiState(),
        )

    init {
        observeScannedDevice(deviceId)
            .onEach { device -> state.update { it.copy(deviceName = device?.displayName) } }
            .launchIn(viewModelScope)

        observeConnection()
            .onEach(::onConnectionChanged)
            .launchIn(viewModelScope)

        observeServices()
            .onEach { services -> state.update { it.copy(services = services) } }
            .launchIn(viewModelScope)

        observeNotifying()
            .onEach { notifying -> state.update { it.copy(notifying = notifying) } }
            .launchIn(viewModelScope)

        observeCharacteristicValues()
            .onEach(::onValueReceived)
            .launchIn(viewModelScope)

        // Failures nobody asked for: link lost, Bluetooth switched off, permission revoked.
        observeGattErrors()
            .onEach { error -> showMessage(error.toUserMessage(), isError = true) }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: GattInspectorEvent) {
        when (event) {
            is GattInspectorEvent.ToggleService -> state.update {
                it.copy(expandedServices = it.expandedServices.toggle(event.serviceUuid))
            }

            is GattInspectorEvent.ToggleCharacteristic -> state.update {
                it.copy(expandedCharacteristics = it.expandedCharacteristics.toggle(event.id))
            }

            is GattInspectorEvent.Read -> read(event.id)
            is GattInspectorEvent.WriteInputChanged -> onWriteInputChanged(event.id, event.input)
            is GattInspectorEvent.Write -> write(event.id)
            is GattInspectorEvent.SetNotifications ->
                toggleNotifications(event.id, event.enabled)
            GattInspectorEvent.Disconnect -> disconnect()
            GattInspectorEvent.DismissMessage -> state.update { it.copy(message = null) }
        }
    }

    private fun read(id: GattCharacteristicId) = runOperation(id, CharacteristicOperation.READING) {
        // The value itself arrives through the characteristic value stream, like a notification
        // would, so there is exactly one path updating the displayed value.
        readCharacteristic(id).onFailure { failure(it, "Read failed") }
    }

    private fun write(id: GattCharacteristicId) {
        val input = state.value.writeInputs[id].orEmpty()
        val bytes = input.parseHexOrNull()
        if (bytes == null) {
            state.update { current ->
                current.copy(
                    writeErrors = current.writeErrors + (id to INVALID_HEX_MESSAGE),
                )
            }
            return
        }

        runOperation(id, CharacteristicOperation.WRITING) {
            writeCharacteristic(id, bytes)
                .onSuccess { showMessage("Wrote ${bytes.size} byte(s).", isError = false) }
                .onFailure { failure(it, "Write failed") }
        }
    }

    private fun toggleNotifications(id: GattCharacteristicId, enabled: Boolean) =
        runOperation(id, CharacteristicOperation.UPDATING_NOTIFICATIONS) {
            setCharacteristicNotifications(id, enabled)
                .onSuccess {
                    showMessage(
                        text = if (enabled) "Notifications enabled." else "Notifications disabled.",
                        isError = false,
                    )
                }
                .onFailure { failure(it, "Could not change notifications") }
        }

    private fun disconnect() {
        viewModelScope.launch {
            disconnectFromDevice().onFailure { failure(it, "Disconnect failed") }
        }
    }

    /**
     * Runs one operation on a characteristic and keeps its busy state honest.
     *
     * A second request for the same characteristic while one is in flight is ignored: the GATT
     * queue would serialize it anyway, and a button that fires twice is never what the user meant.
     */
    private fun runOperation(
        id: GattCharacteristicId,
        operation: CharacteristicOperation,
        block: suspend () -> Unit,
    ) {
        if (state.value.operations[id] != null) return

        state.update { it.copy(operations = it.operations + (id to operation)) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                // Also runs when the screen is left mid-operation, so no row stays stuck busy.
                state.update { it.copy(operations = it.operations - id) }
            }
        }
    }

    private fun onWriteInputChanged(id: GattCharacteristicId, input: String) {
        state.update { current ->
            current.copy(
                writeInputs = current.writeInputs + (id to input),
                // Clear the previous complaint as soon as the user edits the value again.
                writeErrors = current.writeErrors - id,
            )
        }
    }

    private fun onValueReceived(value: GattCharacteristicValue) {
        state.update { current ->
            val log = if (value.source == GattValueSource.NOTIFICATION) {
                val previous = current.notificationLog[value.id].orEmpty()
                // Newest first, bounded: a notifying characteristic can push several values per
                // second and the screen only ever shows the recent ones.
                current.notificationLog + (value.id to (listOf(value) + previous).take(MAX_NOTIFICATIONS))
            } else {
                current.notificationLog
            }
            current.copy(
                latestValues = current.latestValues + (value.id to value),
                notificationLog = log,
            )
        }
    }

    private fun onConnectionChanged(connection: GattConnection) {
        state.update { current ->
            val isGone = connection.state == GattConnectionState.DISCONNECTED
            current.copy(
                connection = connection,
                // Nothing per-characteristic survives a disconnect: the handles are gone and the
                // values would be stale the moment they are shown.
                operations = if (isGone) emptyMap() else current.operations,
                writeErrors = if (isGone) emptyMap() else current.writeErrors,
                notifying = if (isGone) emptySet() else current.notifying,
            )
        }
    }

    private fun failure(throwable: Throwable, prefix: String) {
        showMessage("$prefix: ${throwable.toBleError().toUserMessage()}", isError = true)
    }

    private fun showMessage(text: String, isError: Boolean) {
        state.update { it.copy(message = UiMessage(text, isError)) }
    }

    private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MAX_NOTIFICATIONS = 20
        const val INVALID_HEX_MESSAGE = "Enter an even number of hex digits, e.g. 01 FF 2A."
    }
}

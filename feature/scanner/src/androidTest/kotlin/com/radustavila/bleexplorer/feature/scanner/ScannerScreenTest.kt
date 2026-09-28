package com.radustavila.bleexplorer.feature.scanner

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.radustavila.bleexplorer.domain.model.BleDevice
import com.radustavila.bleexplorer.domain.model.BleError
import com.radustavila.bleexplorer.domain.model.BlePermissionState
import com.radustavila.bleexplorer.domain.model.BluetoothState
import com.radustavila.bleexplorer.domain.model.GattConnection
import com.radustavila.bleexplorer.domain.model.GattConnectionState
import com.radustavila.bleexplorer.domain.model.KnownDevice
import org.junit.Rule
import org.junit.Test

/**
 * Rendering tests for the scanner screen. They run against a fixed [ScannerUiState], so the list,
 * empty and error states can be verified without any Bluetooth hardware.
 */
class ScannerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun device(id: String, name: String?, rssi: Int) = BleDevice(
        id = id,
        address = id,
        name = name,
        rssi = rssi,
        txPowerDbm = null,
        isConnectable = true,
    )

    private val readyState = ScannerUiState(
        bluetoothState = BluetoothState.ENABLED,
        permissionState = BlePermissionState.GRANTED,
    )

    @Test
    fun deviceRowsShowNameAndRssi() {
        composeRule.setContent {
            ScannerScreen(
                state = readyState.copy(
                    devices = listOf(
                        device("AA", "Venu Sq", -52),
                        device("BB", null, -81),
                    ),
                ),
                onEvent = {},
                onDeviceClick = {},
                onRequestPermissions = {},
                onEnableBluetooth = {},
                onOpenSettings = {},
            )
        }

        composeRule.onNodeWithText("Venu Sq").assertIsDisplayed()
        composeRule.onNodeWithText("Unknown device").assertIsDisplayed()
        // The RSSI row is exposed to accessibility as a single, readable description.
        composeRule.onNodeWithContentDescription("Strong signal, -52 dBm").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Weak signal, -81 dBm").assertIsDisplayed()
        composeRule.onNodeWithText("2 devices found").assertIsDisplayed()
    }

    @Test
    fun theDeviceCountIsSingularForASingleDevice() {
        composeRule.setContent {
            ScannerScreen(
                state = readyState.copy(devices = listOf(device("AA", "Venu Sq", -52))),
                onEvent = {},
                onDeviceClick = {},
                onRequestPermissions = {},
                onEnableBluetooth = {},
                onOpenSettings = {},
            )
        }

        composeRule.onNodeWithText("1 device found").assertIsDisplayed()
    }

    @Test
    fun tappingADeviceReportsItsStableId() {
        var clickedId: String? = null
        composeRule.setContent {
            ScannerScreen(
                state = readyState.copy(devices = listOf(device("AA", "Venu Sq", -52))),
                onEvent = {},
                onDeviceClick = { clickedId = it },
                onRequestPermissions = {},
                onEnableBluetooth = {},
                onOpenSettings = {},
            )
        }

        composeRule.onNodeWithText("Venu Sq").performClick()

        assertThat(clickedId).isEqualTo("AA")
    }

    @Test
    fun startButtonEmitsStartScan() {
        var event: ScannerEvent? = null
        composeRule.setContent {
            ScannerScreen(
                state = readyState,
                onEvent = { event = it },
                onDeviceClick = {},
                onRequestPermissions = {},
                onEnableBluetooth = {},
                onOpenSettings = {},
            )
        }

        composeRule.onNodeWithText("Start scan").performClick()

        assertThat(event).isEqualTo(ScannerEvent.StartScan)
    }

    @Test
    fun bluetoothDisabledStateOffersToEnableIt() {
        var enableRequested = false
        composeRule.setContent {
            ScannerScreen(
                state = readyState.copy(bluetoothState = BluetoothState.DISABLED),
                onEvent = {},
                onDeviceClick = {},
                onRequestPermissions = {},
                onEnableBluetooth = { enableRequested = true },
                onOpenSettings = {},
            )
        }

        composeRule.onNodeWithText("Bluetooth is disabled.").assertIsDisplayed()
        composeRule.onNodeWithText("Enable Bluetooth").performClick()

        assertThat(enableRequested).isTrue()
    }

    // --- Known device ---------------------------------------------------------------------

    private val knownState = readyState.copy(knownDevice = KnownDevice("AA", "ESP32-BLE-POC"))

    private fun setScreen(
        state: ScannerUiState,
        onEvent: (ScannerEvent) -> Unit = {},
        onDeviceClick: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            ScannerScreen(
                state = state,
                onEvent = onEvent,
                onDeviceClick = onDeviceClick,
                onRequestPermissions = {},
                onEnableBluetooth = {},
                onOpenSettings = {},
            )
        }
    }

    @Test
    fun theKnownDeviceIsShownWithoutAScanAndCanBeConnected() {
        var event: ScannerEvent? = null
        setScreen(knownState, onEvent = { event = it })

        composeRule.onNodeWithText("Known device").assertIsDisplayed()
        composeRule.onNodeWithText("ESP32-BLE-POC").assertIsDisplayed()
        composeRule.onNodeWithText("Disconnected").assertIsDisplayed()
        // The manual scan stays available next to it.
        composeRule.onNodeWithText("Start scan").assertIsDisplayed()

        composeRule.onNodeWithText("Connect").performClick()

        assertThat(event).isEqualTo(ScannerEvent.ConnectKnownDevice)
    }

    @Test
    fun aScannedKnownDeviceIsAvailableAndNotListedTwice() {
        setScreen(
            knownState.copy(
                devices = listOf(device("AA", "ESP32-BLE-POC", -48), device("BB", "Venu Sq", -70)),
            ),
        )

        composeRule.onAllNodesWithText("ESP32-BLE-POC").assertCountEquals(1)
        composeRule.onNodeWithText("Available").assertIsDisplayed()
        composeRule.onNodeWithText("Venu Sq").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Strong signal, -48 dBm").assertIsDisplayed()
    }

    @Test
    fun aConnectedKnownDeviceCanBeOpenedAndDisconnected() {
        var event: ScannerEvent? = null
        var openedId: String? = null
        setScreen(
            knownState.copy(
                connection = GattConnection("AA", GattConnectionState.CONNECTED, true),
            ),
            onEvent = { event = it },
            onDeviceClick = { openedId = it },
        )

        composeRule.onNodeWithText("Connected").assertIsDisplayed()
        composeRule.onNodeWithText("Open").performClick()
        assertThat(openedId).isEqualTo("AA")

        composeRule.onNodeWithText("Disconnect").performClick()
        assertThat(event).isEqualTo(ScannerEvent.DisconnectKnownDevice)
    }

    @Test
    fun aConnectingKnownDeviceCanBeCancelled() {
        var event: ScannerEvent? = null
        setScreen(knownState.copy(isKnownDeviceConnectRequested = true), onEvent = { event = it })

        composeRule.onNodeWithText("Connecting…").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        assertThat(event).isEqualTo(ScannerEvent.DisconnectKnownDevice)
    }

    @Test
    fun anUnreachableKnownDeviceSaysWhy() {
        setScreen(
            knownState.copy(
                knownDeviceProblem = BleError.ConnectionTimeout,
            ),
        )

        composeRule.onNodeWithText("Unavailable").assertIsDisplayed()
        composeRule
            .onNodeWithText("The device did not answer in time. It may be out of range.")
            .assertIsDisplayed()
    }

    @Test
    fun theKnownDeviceCannotBeConnectedWhileBluetoothIsOff() {
        setScreen(knownState.copy(bluetoothState = BluetoothState.DISABLED))

        composeRule.onNodeWithText("Bluetooth is off").assertIsDisplayed()
        composeRule.onNodeWithText("Connect").assertIsNotEnabled()
    }
}

package com.radustavila.bleexplorer.feature.gattinspector

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.radustavila.bleexplorer.domain.model.GattCharacteristicId
import com.radustavila.bleexplorer.domain.model.GattConnectionState
import org.junit.Rule
import org.junit.Test

class GattInspectorScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val batteryLevelId = GattCharacteristicId(
        serviceUuid = "0000180f-0000-1000-8000-00805f9b34fb",
        characteristicUuid = "00002a19-0000-1000-8000-00805f9b34fb",
    )

    private val batteryLevel = GattCharacteristicUi(
        id = batteryLevelId,
        uuid = batteryLevelId.characteristicUuid,
        shortUuid = "2A19",
        name = "Battery Level",
        properties = listOf("READ", "NOTIFY"),
        canRead = true,
        canWrite = false,
        writeLabel = "Write (with response)",
        supportsSubscription = true,
        isNotifying = false,
        isExpanded = true,
        operation = CharacteristicOperation.IDLE,
        latestValue = CharacteristicValueUi(
            hex = "4C",
            byteCount = 1,
            utf8 = null,
            decoded = "76%",
            timestamp = "12:31:04",
            fromNotification = false,
        ),
        notifications = emptyList(),
        writeInput = "",
        writeError = null,
    )

    private val batteryService = GattServiceUi(
        uuid = batteryLevelId.serviceUuid,
        shortUuid = "180F",
        name = "Battery Service",
        isExpanded = true,
        characteristics = listOf(batteryLevel),
    )

    @Test
    fun showsServicesCharacteristicsAndValues() {
        setScreen(state(services = listOf(batteryService)))

        composeRule.onNodeWithText("Connected").assertIsDisplayed()
        composeRule.onNodeWithText("Battery Service").assertIsDisplayed()
        composeRule.onNodeWithText("Battery Level").assertIsDisplayed()
        composeRule.onNodeWithText("4C").assertIsDisplayed()
        composeRule.onNodeWithText("Decoded: 76%").assertIsDisplayed()
        composeRule.onNodeWithText("1 byte(s) · 12:31:04").assertIsDisplayed()
    }

    @Test
    fun readingEmitsTheEvent() {
        val events = mutableListOf<GattInspectorEvent>()
        setScreen(state(services = listOf(batteryService)), onEvent = events::add)

        composeRule.onNodeWithText("Read").performClick()

        assertThat(events).contains(GattInspectorEvent.Read(batteryLevelId))
    }

    @Test
    fun actionsAreDisabledWhileAnOperationIsRunning() {
        val busy = batteryService.copy(
            characteristics = listOf(
                batteryLevel.copy(operation = CharacteristicOperation.READING),
            ),
        )
        setScreen(state(services = listOf(busy)))

        composeRule.onNodeWithText("Reading…").assertIsNotEnabled()
    }

    @Test
    fun anUnknownCharacteristicIsShownByItsUuid() {
        val unknown = batteryService.copy(
            uuid = "12345678-1234-5678-1234-56789abcdef0",
            shortUuid = "12345678",
            name = null,
            characteristics = listOf(
                batteryLevel.copy(
                    name = null,
                    uuid = "12345678-1234-5678-1234-56789abcdef1",
                    latestValue = null,
                ),
            ),
        )
        setScreen(state(services = listOf(unknown)))

        composeRule.onNodeWithText("Unknown service").assertIsDisplayed()
        composeRule.onNodeWithText("Unknown characteristic").assertIsDisplayed()
        composeRule.onNodeWithText("12345678-1234-5678-1234-56789abcdef1").assertIsDisplayed()
    }

    @Test
    fun notificationValuesAreListed() {
        val notifying = batteryService.copy(
            characteristics = listOf(
                batteryLevel.copy(
                    isNotifying = true,
                    notifications = listOf(
                        NotificationUi(timestamp = "12:31:06", hex = "4C 00", decoded = null),
                        NotificationUi(timestamp = "12:31:05", hex = "4A 00", decoded = null),
                    ),
                ),
            ),
        )
        setScreen(state(services = listOf(notifying)))

        composeRule.onNodeWithText("Notifications ON").assertIsDisplayed()
        composeRule.onNodeWithText("12:31:06  4C 00").assertIsDisplayed()
        composeRule.onNodeWithText("12:31:05  4A 00").assertIsDisplayed()
    }

    @Test
    fun aDeviceWithoutServicesIsExplained() {
        setScreen(state(services = emptyList()))

        composeRule.onNodeWithText("No services").assertIsDisplayed()
    }

    @Test
    fun serviceDiscoveryShowsProgress() {
        setScreen(state(services = emptyList(), isDiscovering = true))

        composeRule.onNodeWithText("Discovering services…").assertIsDisplayed()
    }

    @Test
    fun losingTheConnectionOffersAWayBack() {
        setScreen(
            GattInspectorUiState(
                deviceName = "Venu Sq",
                connectionState = GattConnectionState.DISCONNECTED,
                isDiscoveringServices = false,
                services = emptyList(),
            ),
        )

        composeRule.onNodeWithText("Not connected").assertIsDisplayed()
        composeRule.onNodeWithText("Back").assertIsDisplayed()
    }

    private fun state(
        services: List<GattServiceUi>,
        isDiscovering: Boolean = false,
    ): GattInspectorUiState = GattInspectorUiState(
        deviceName = "Venu Sq",
        connectionState = GattConnectionState.CONNECTED,
        isDiscoveringServices = isDiscovering,
        services = services,
    )

    private fun setScreen(
        state: GattInspectorUiState,
        onEvent: (GattInspectorEvent) -> Unit = {},
    ) {
        composeRule.setContent {
            GattInspectorScreen(state = state, onEvent = onEvent, onBackClick = {})
        }
    }
}

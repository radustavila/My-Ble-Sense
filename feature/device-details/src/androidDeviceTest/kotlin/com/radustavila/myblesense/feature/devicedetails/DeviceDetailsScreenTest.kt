package com.radustavila.myblesense.feature.devicedetails

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class DeviceDetailsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val device = DeviceDetailsUi(
        id = "AA:BB:CC:DD:EE:FF",
        name = "Venu Sq",
        isNameKnown = true,
        rssiDbm = -52,
        signalLabel = "Strong",
        address = "AA:BB:CC:DD:EE:FF",
        txPower = "4 dBm",
        connectable = "Yes",
        advertisementCount = 12,
        serviceUuids = listOf("180D"),
        manufacturerData = listOf(
            ManufacturerDataUi(
                companyId = "0x0087",
                companyName = "Garmin International",
                payloadHex = "01 02 03",
                payloadSize = 3,
            ),
        ),
        serviceData = listOf(
            ServiceDataUi(uuid = "FE9F", payloadHex = "0A 0B", payloadSize = 2),
        ),
    )

    @Test
    fun showsAdvertisingInformation() {
        composeRule.setContent {
            DeviceDetailsScreen(
                state = DeviceDetailsUiState(isLoading = false, device = device),
                onEvent = {},
                onBackClick = {},
                onOpenGattInspector = {},
            )
        }

        composeRule.onNodeWithText("-52 dBm (Strong)").assertIsDisplayed()
        composeRule.onNodeWithText("180D").assertIsDisplayed()
        composeRule.onNodeWithText("01 02 03").assertIsDisplayed()
        composeRule.onNodeWithText("3 bytes, proprietary payload — not decoded").assertIsDisplayed()
        composeRule.onNodeWithText("0A 0B").assertIsDisplayed()
        composeRule.onNodeWithText("4 dBm").assertIsDisplayed()
    }

    @Test
    fun showsTheFullHardwareAddress() {
        composeRule.setContent {
            DeviceDetailsScreen(
                state = DeviceDetailsUiState(isLoading = false, device = device),
                onEvent = {},
                onBackClick = {},
                onOpenGattInspector = {},
            )
        }

        composeRule.onNodeWithText("AA:BB:CC:DD:EE:FF").assertIsDisplayed()
    }

    @Test
    fun connectIsOfferedWhileDisconnected() {
        composeRule.setContent {
            DeviceDetailsScreen(
                state = DeviceDetailsUiState(isLoading = false, device = device),
                onEvent = {},
                onBackClick = {},
                onOpenGattInspector = {},
            )
        }

        composeRule.onNodeWithText("Disconnected").assertIsDisplayed()
        composeRule.onNodeWithText("Connect").assertIsDisplayed()
    }

    @Test
    fun theInspectorIsOnlyReachableWhileConnected() {
        composeRule.setContent {
            DeviceDetailsScreen(
                state = DeviceDetailsUiState(
                    isLoading = false,
                    device = device,
                    connection = ConnectionUiState(status = ConnectionStatus.CONNECTED),
                ),
                onEvent = {},
                onBackClick = {},
                onOpenGattInspector = {},
            )
        }

        composeRule.onNodeWithText("Connected").assertIsDisplayed()
        composeRule.onNodeWithText("Open GATT Inspector").assertIsDisplayed()
        composeRule.onNodeWithText("Disconnect").assertIsDisplayed()
    }

    @Test
    fun missingDeviceShowsAnExplanation() {
        composeRule.setContent {
            DeviceDetailsScreen(
                state = DeviceDetailsUiState(isLoading = false, device = null),
                onEvent = {},
                onBackClick = {},
                onOpenGattInspector = {},
            )
        }

        composeRule.onNodeWithText("Device no longer available").assertIsDisplayed()
    }

    @Test
    fun pocServiceShowsStatusAndReadAction() {
        val events = mutableListOf<DeviceDetailsEvent>()
        composeRule.setContent {
            DeviceDetailsScreen(
                state = DeviceDetailsUiState(
                    isLoading = false,
                    device = device,
                    connection = ConnectionUiState(status = ConnectionStatus.CONNECTED),
                    poc = PocServiceUiState(
                        hasStatus = true,
                        hasCommand = true,
                        hasEvents = true,
                        canReadStatus = true,
                        status = "ONLINE",
                    ),
                ),
                onEvent = { events += it },
                onBackClick = {},
                onOpenGattInspector = {},
            )
        }

        composeRule.onNodeWithText("POC Service").assertIsDisplayed()
        composeRule.onNodeWithText("ONLINE").assertIsDisplayed()
        composeRule.onNodeWithText("Read Status").performClick()

        assert(DeviceDetailsEvent.ReadPocStatus in events)
    }

    @Test
    fun sendPingSendsTheCommandEvent() {
        val events = mutableListOf<DeviceDetailsEvent>()
        setPocContent(pocState(), onEvent = { events += it })

        composeRule.onNodeWithText("Send PING").assertIsEnabled().performClick()

        assert(DeviceDetailsEvent.SendPocPing in events)
    }

    @Test
    fun sendPingIsDisabledWhileSending() {
        setPocContent(pocState(commandState = PocCommandState.Sending))

        composeRule.onNodeWithText("Send PING").assertIsNotEnabled()
        composeRule.onNodeWithText("Sending…").assertIsDisplayed()
    }

    @Test
    fun aSentCommandIsConfirmed() {
        setPocContent(pocState(commandState = PocCommandState.Sent))

        composeRule.onNodeWithText("Command sent").assertIsDisplayed()
    }

    @Test
    fun aFailedCommandShowsTheErrorAndCanBeRetried() {
        setPocContent(
            pocState(commandState = PocCommandState.Failed("Could not send command: Write failed.")),
        )

        composeRule.onNodeWithText("Could not send command: Write failed.").assertIsDisplayed()
        composeRule.onNodeWithText("Send PING").assertIsEnabled()
    }

    @Test
    fun sendPingIsDisabledWithoutTheCommandCharacteristic() {
        setPocContent(pocState(hasCommand = false, canSendCommand = false))

        composeRule.onNodeWithText("Send PING").assertIsNotEnabled()
        composeRule.onNodeWithText("Command characteristic not found").assertIsDisplayed()
    }

    @Test
    fun hardwareEventsAreListedInOrderWithEachTouchGrouped() {
        setPocContent(
            pocState(isEventsNotifying = true),
            hardwareEvents = HardwareEventsUiState(
                entries = listOf(
                    HardwareEventEntryUi.Touch(
                        key = 1,
                        pressedAt = "10:00:01.000",
                        releasedAt = "10:00:02.800",
                        duration = "1.8s",
                        intensity = "Peak 94% · Avg 81%",
                    ),
                    HardwareEventEntryUi.Single(3, type = "EVENT 3", timestamp = "10:00:03.000"),
                ),
                lastTouch = TouchStatsUi(
                    duration = "Duration: 1.8s",
                    peak = "Peak: 94%",
                    average = "Average: 81%",
                ),
            ),
        )

        composeRule.onNodeWithText("Hardware Events").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Listening for events").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Duration: 1.8s").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Peak: 94%").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Average: 81%").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Peak 94% · Avg 81%").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("TOUCH_ON").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("10:00:02.800").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("1.8s").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("EVENT 3").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Subscribe").assertDoesNotExist()

        val bounds = { text: String -> composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot }
        assertThat(bounds("TOUCH_ON").top).isLessThan(bounds("TOUCH_OFF").top)
        assertThat(bounds("TOUCH_OFF").top).isLessThan(bounds("EVENT 3").top)
        // Event names start in the same column whatever the timestamp digits.
        assertThat(bounds("TOUCH_OFF").left).isEqualTo(bounds("TOUCH_ON").left)
        assertThat(bounds("EVENT 3").left).isEqualTo(bounds("TOUCH_ON").left)
    }

    @Test
    fun anUnpairedTouchShowsTheMissingEdge() {
        setPocContent(
            pocState(isEventsNotifying = true),
            hardwareEvents = HardwareEventsUiState(
                entries = listOf(HardwareEventEntryUi.Touch(key = 1, pressedAt = "10:00:01.000")),
            ),
        )

        composeRule.onNodeWithText("TOUCH_OFF").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Not received").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aHeldTouchIsShownAsPressed() {
        setPocContent(
            pocState(isEventsNotifying = true),
            hardwareEvents = HardwareEventsUiState(
                entries = listOf(
                    HardwareEventEntryUi.Touch(key = 1, pressedAt = "10:00:01.000", isActive = true),
                ),
                isTouchPressed = true,
            ),
        )

        composeRule.onNodeWithText("Touch: pressed").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Holding…").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Intensity: —").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Duration: —").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Peak: —").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Average: —").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theLiveTouchIntensityIsShownAsAProgressIndicator() {
        setPocContent(
            pocState(isEventsNotifying = true),
            hardwareEvents = HardwareEventsUiState(
                entries = listOf(
                    HardwareEventEntryUi.Touch(key = 1, pressedAt = "10:00:01.000", isActive = true),
                ),
                isTouchPressed = true,
                liveIntensityPercent = 94,
            ),
        )

        composeRule.onNodeWithText("Intensity: 94%").performScrollTo().assertIsDisplayed()
        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.94f, 0f..1f)))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("TOUCH_INTENSITY:94").assertDoesNotExist()
    }

    @Test
    fun theEventHistoryStaysVisibleWhileDisconnected() {
        composeRule.setContent {
            DeviceDetailsScreen(
                state = DeviceDetailsUiState(
                    isLoading = false,
                    device = device,
                    hardwareEvents = HardwareEventsUiState(
                        entries = listOf(
                            HardwareEventEntryUi.Touch(key = 1, pressedAt = "10:00:01.000"),
                        ),
                    ),
                ),
                onEvent = {},
                onBackClick = {},
                onOpenGattInspector = {},
            )
        }

        composeRule.onNodeWithText("Hardware Events").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Not connected").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("TOUCH_ON").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun reconnectingShowsTheAttemptAndCanBeStopped() {
        val events = mutableListOf<DeviceDetailsEvent>()
        composeRule.setContent {
            DeviceDetailsScreen(
                state = DeviceDetailsUiState(
                    isLoading = false,
                    device = device,
                    connection = ConnectionUiState(
                        status = ConnectionStatus.RECONNECTING,
                        reconnection = ReconnectionUi(attempt = 2, maxAttempts = 3),
                        problem = "The device disconnected.",
                    ),
                ),
                onEvent = { events += it },
                onBackClick = {},
                onOpenGattInspector = {},
            )
        }

        composeRule.onNodeWithText("Reconnecting… (attempt 2 of 3)").assertIsDisplayed()
        composeRule.onNodeWithText("The device disconnected.").assertIsDisplayed()
        composeRule.onNodeWithText("Connect").assertDoesNotExist()
        composeRule.onNodeWithText("Stop reconnecting").performClick()

        assert(DeviceDetailsEvent.Disconnect in events)
    }

    @Test
    fun aFailedReconnectionExplainsWhyAndOffersConnect() {
        composeRule.setContent {
            DeviceDetailsScreen(
                state = DeviceDetailsUiState(
                    isLoading = false,
                    device = device,
                    connection = ConnectionUiState(
                        problem = "Could not reconnect: The device did not answer in time.",
                    ),
                ),
                onEvent = {},
                onBackClick = {},
                onOpenGattInspector = {},
            )
        }

        composeRule.onNodeWithText("Disconnected").assertIsDisplayed()
        composeRule.onNodeWithText("Could not reconnect: The device did not answer in time.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Connect").assertIsEnabled()
    }

    @Test
    fun bluetoothOffDisablesConnect() {
        composeRule.setContent {
            DeviceDetailsScreen(
                state = DeviceDetailsUiState(
                    isLoading = false,
                    device = device,
                    connection = ConnectionUiState(isBluetoothEnabled = false),
                ),
                onEvent = {},
                onBackClick = {},
                onOpenGattInspector = {},
            )
        }

        composeRule.onNodeWithText("Bluetooth is off").assertIsDisplayed()
        composeRule.onNodeWithText("Turn Bluetooth on to connect.").assertIsDisplayed()
        composeRule.onNodeWithText("Connect").assertIsNotEnabled()
    }

    @Test
    fun aSubscriptionWithoutEventsSaysSo() {
        setPocContent(pocState(isEventsNotifying = true))

        composeRule.onNodeWithText("No events received yet").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aFailedSubscriptionShowsTheErrorAndCanBeRetried() {
        val events = mutableListOf<DeviceDetailsEvent>()
        setPocContent(
            pocState(eventsError = "Could not subscribe to events: Write failed."),
            onEvent = { events += it },
        )

        composeRule.onNodeWithText("Could not subscribe to events: Write failed.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Subscribe").performScrollTo().assertIsEnabled().performClick()

        assert(DeviceDetailsEvent.SubscribePocEvents in events)
    }

    @Test
    fun subscribeIsDisabledWhileSubscribing() {
        setPocContent(pocState(isSubscribingEvents = true))

        composeRule.onNodeWithText("Subscribing…").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Subscribe").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun aMissingEventsCharacteristicIsReported() {
        setPocContent(pocState(hasEvents = false, canSubscribeEvents = false))

        composeRule.onNodeWithText("Events characteristic not found")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Subscribe").assertDoesNotExist()
    }

    private fun pocState(
        hasCommand: Boolean = true,
        canSendCommand: Boolean = true,
        commandState: PocCommandState = PocCommandState.Idle,
        hasEvents: Boolean = true,
        canSubscribeEvents: Boolean = true,
        isEventsNotifying: Boolean = false,
        isSubscribingEvents: Boolean = false,
        eventsError: String? = null,
    ) = PocServiceUiState(
        hasStatus = true,
        hasCommand = hasCommand,
        hasEvents = hasEvents,
        canReadStatus = true,
        status = "ONLINE",
        canSendCommand = canSendCommand,
        commandState = commandState,
        canSubscribeEvents = canSubscribeEvents,
        isEventsNotifying = isEventsNotifying,
        isSubscribingEvents = isSubscribingEvents,
        eventsError = eventsError,
    )

    private fun setPocContent(
        poc: PocServiceUiState,
        onEvent: (DeviceDetailsEvent) -> Unit = {},
        hardwareEvents: HardwareEventsUiState = HardwareEventsUiState(),
    ) {
        composeRule.setContent {
            DeviceDetailsScreen(
                state = DeviceDetailsUiState(
                    isLoading = false,
                    device = device,
                    connection = ConnectionUiState(status = ConnectionStatus.CONNECTED),
                    poc = poc,
                    hardwareEvents = hardwareEvents,
                ),
                onEvent = onEvent,
                onBackClick = {},
                onOpenGattInspector = {},
            )
        }
    }
}

# CLAUDE.md

Guidance for working in this repository. `README.md` is the full design document (BLE flow, GATT
lifecycle, error handling, test inventory); read the relevant section before changing BLE code.

## Build & test

This machine's `JAVA_HOME` is stale — export it before any Gradle command:

```bash
export JAVA_HOME=~/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home

./gradlew test                      # all JVM unit tests (domain, core, features)
./gradlew assembleDebug lintDebug   # the checks every change must pass
./gradlew :feature:device-details:testDebugUnitTest --tests '*DeviceDetailsViewModelTest*'
```

Compose UI tests (`connectedDebugAndroidTest`) **fail on API 37 emulators** (Espresso 3.7.0 calls a
removed `InputManager.getInstance`). Run them on an API ≤ 36 AVD:

```bash
~/Library/Android/sdk/emulator/emulator -avd Pixel_7_Pro_API_35_2 -no-window -no-audio -no-snapshot-save &
ANDROID_SERIAL=emulator-XXXX ./gradlew connectedDebugAndroidTest
# /opt/homebrew/bin/emulator is a stale legacy wrapper and gets killed: use the SDK binary.
```

A physical phone must be unlocked, or the tests report "No compose hierarchies found".
The emulator has no Bluetooth: scanning/connecting only works on real hardware (ESP32 POC board).

## Architecture rules

- `:domain` is a plain Kotlin JVM module — no Android, no Compose. Models, repository interfaces,
  use cases and pure rules (sorting, touch timing, reconnect policy) live here.
- Android Bluetooth/GATT types never leave `:core:bluetooth`. `AndroidBleGattClient` is the only
  class touching `BluetoothGatt`; every GATT operation goes through `GattOperationQueue`.
- Features depend on `:domain`, `:core:common`, `:core:designsystem` — never on each other; only
  `:app` wires navigation between them.
- ViewModels own screen state (`MutableStateFlow<UiState>`) and call use cases; use cases return
  `Result` with a `BleException(BleError)` inside. Composables only render state and send events.
- Formatting (hex, timestamps, durations, labels) lives in the feature's `*Mapper.kt` as pure
  functions, so it is unit-tested without Compose.
- User-facing error copy comes from `BleError.toUserMessage()` in `:core:designsystem`; add new
  errors there rather than hard-coding strings.

## Connection model (easy to break)

- One app-scoped GATT connection. **Whoever opens a link closes it**, in `onCleared` via the
  `@ApplicationScope` scope (the `viewModelScope` is already cancelled there):
  `DeviceDetailsViewModel` closes links it opened (`closesLinkOnLeave`) and leaves others open,
  only disabling its POC Events CCCD; `ScannerViewModel` closes the known-device link it opened.
- `BleGattClient.errors()` emits **only unexpected** losses — a user-requested disconnect never
  emits. The details ViewModel relies on this to trigger `ReconnectToDeviceUseCase`, which is
  bounded by `ReconnectPolicy` (3 attempts, 1/2/4 s back-off). Never add unbounded retries.
- The UI connection status is derived from the client's `connection` StateFlow (+ reconnection +
  adapter state) in `toConnectionStatus`, not from what the user tapped.
- After every (re)connection the POC Events CCCD is re-subscribed when the service is newly
  discovered (`onPocServiceChanged`); the CCCD is disabled before an explicit disconnect.

## Known device

- `KnownDevice(id, name)` + `KnownDeviceRepository` in `:domain`; DataStore implementation in
  `:core:datastore` (only strings — never persist Android BLE objects).
- `TrackKnownDeviceUseCase` (app scope, started in `BleExplorerApplication`) saves every connection
  that reaches services discovered and merges scan results with the known id. Don't save from
  ViewModels.
- `ScannerViewModel` shows it before any scan and auto-connects **once** per ViewModel via
  `shouldAutoConnect` (Bluetooth ready, idle link). No reconnect loop on the list. Status and copy
  come from `KnownDeviceMapper.kt`; scan results with the known id are excluded via `otherDevices`.
- The details screen falls back to the known device when the scan session lacks it
  (`rssiDbm == null`).

## ESP32 POC peripheral

Profile in `domain/.../model/PocService.kt` (`PocProfile` UUIDs: Status READ, Command WRITE,
Events NOTIFY). Firmware is not in this repo. Events are UTF-8 text from the capacitive touch
sensor: `TOUCH_ON`, then `TOUCH_INTENSITY:<0..100>` about every 100 ms while held, then
`TOUCH_OFF` (plus any other text, shown raw — a malformed/out-of-range intensity is `Other`, never
clamped). Parsing is `PocTouchEvents.parseIntensity`.

- `TouchTracker` (`HardwareEvent.kt`, one instance per connection via
  `ObservePocHardwareEventsUseCase`) computes duration from notification arrival times and
  peak/average from a `TouchIntensityAccumulator` (running aggregates, no sample list). The
  `TOUCH_OFF` becomes `TouchReleased(durationMillis, intensity: TouchIntensityStats?)`. Samples
  without an observed `TOUCH_ON` are shown live but not summarised. Don't move timing or
  statistics into the ViewModel or Compose.
- `HardwareEvent.TouchIntensity` samples only update `HardwareEventsUiState.liveIntensityPercent`
  (the live `LinearProgressIndicator`); they are **not** added to the history list (10/s would
  evict everything). History is `entries: List<HardwareEventEntryUi>`: a `Touch` entry pairs
  `TOUCH_ON` + `TOUCH_OFF` (built in `HardwareEventsUiState.plus` in the mapper; the release
  completes the `isActive` entry), other text is `Single`. A superseded press or a lost link ends
  the active touch via `endActiveTouch()` ("Not received"). The summary shows `lastTouch:
  TouchStatsUi` (`Duration:`, `Peak:`, `Average:`).
- Event lines use a fixed-width timestamp column (measured `88:88:88.888`, tabular figures) so
  names align. The intensity bar sets `drawStopIndicator = {}`, `gapSize = 0.dp` and an explicit
  track colour — M3 1.4 defaults draw a stray dot and a track invisible on the card.

## Tests

- Fakes are shared as `:domain` test fixtures (`FakeBleGattClient`, `FakeBleGattRepository`,
  `FakeBleScanRepository`, `FakeKnownDeviceRepository`, `MainDispatcherRule`); no test needs BLE hardware. Use
  `testFixtures(projects.domain)` in a module's `testImplementation`.
- ViewModel tests run on `MainDispatcherRule` (unconfined) sharing the `runTest` scheduler, so
  back-off delays and timeouts are driven with `advanceTimeBy`.
- Keep test names as backtick sentences describing behaviour, matching existing suites.

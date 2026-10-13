# CLAUDE.md

Guidance for working in this repository. `README.md` is the full design document (BLE flow, GATT
lifecycle, error handling, test inventory); read the relevant section before changing BLE code.

## Build & test

This machine's `JAVA_HOME` is stale — export it before any Gradle command:

```bash
export JAVA_HOME=~/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home

./gradlew test                      # all JVM unit tests (testAndroidHostTest of every module)
./gradlew assembleDebug lintDebug   # the checks every change must pass
./gradlew :feature:device-details:testAndroidHostTest --tests '*DeviceDetailsViewModelTest*'
```

The project is Kotlin Multiplatform + Compose Multiplatform (Android + iOS). iOS needs Xcode;
`xcode-select` points at the Command Line Tools on this machine, so export `DEVELOPER_DIR`:

```bash
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer

./gradlew iosSimulatorArm64Test                          # common tests on Kotlin/Native
./gradlew :shared:app:linkDebugFrameworkIosSimulatorArm64  # does the iOS side still link?
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 17' build
```

A change to shared code must pass on both targets: run the iOS test task too, not only `test`.

Compose UI tests (`connectedAndroidDeviceTest`, sources in `src/androidDeviceTest`) **fail on API 37 emulators** (Espresso 3.7.0 calls a
removed `InputManager.getInstance`). Run them on an API ≤ 36 AVD:

```bash
~/Library/Android/sdk/emulator/emulator -avd Pixel_7_Pro_API_35_2 -no-window -no-audio -no-snapshot-save &
ANDROID_SERIAL=emulator-XXXX ./gradlew connectedAndroidDeviceTest
# /opt/homebrew/bin/emulator is a stale legacy wrapper and gets killed: use the SDK binary.
```

A physical phone must be unlocked, or the tests report "No compose hierarchies found".
Neither the Android emulator nor the iOS simulator has Bluetooth (the simulator reports it as
unsupported): scanning/connecting only works on real hardware (ESP32 POC board).

## Architecture rules

- Every module except `:app` is a KMP module (`myblesense.kmp.library` / `.kmp.compose` /
  `.kmp.feature` convention plugins): shared code in `commonMain`, platform code in `androidMain`
  / `iosMain`. `:app` is the Android shell, `iosApp/` the Xcode shell, `:shared:app` holds
  `App()`, the nav graph and the Koin graph (`startApp`) and produces the `Shared` framework.
- `:domain` is `commonMain` only — no Android, no CoreBluetooth, no Compose, no DI framework.
  Models, repository interfaces, use cases and pure rules (sorting, touch timing, reconnect
  policy) live here. Its use cases are wired in `:shared:app` (`DomainModule.kt`).
- Platform Bluetooth types never leave `:core:bluetooth`'s platform source sets.
  `AndroidBleGattClient` is the only class touching `BluetoothGatt`, `IosBleGattClient` (+
  `IosCentral`, the single `CBCentralManager`) the only ones touching CoreBluetooth; every GATT
  operation goes through `GattOperationQueue` (`commonMain`) on both.
- Features depend on `:domain`, `:core:common`, `:core:designsystem` — never on each other; only
  `:shared:app` wires navigation between them.
- ViewModels own screen state (`MutableStateFlow<UiState>`) and call use cases; use cases return
  `Result` with a `BleException(BleError)` inside. Composables only render state and send events.
- DI is Koin: each module exposes a `Module` (`commonModule`, `bluetoothModule` with an
  `expect`/`actual` platform half, `dataStoreModule`, `scannerModule`, …). The application scope
  and dispatchers are qualified (`ApplicationScope`, `IoDispatcher` in `:core:common`).
- Common code has no `java.*`: use `formatDecimal` / `formatClockTime` / `currentTimeMillis`
  (`:core:common`) instead of `String.format`, `java.time`, `System.currentTimeMillis`.
- Formatting (hex, timestamps, durations, labels) lives in the feature's `*Mapper.kt` as pure
  functions, so it is unit-tested without Compose.
- User-facing error copy comes from `BleError.toUserMessage()` in `:core:designsystem`; add new
  errors there rather than hard-coding strings.

## iOS specifics (easy to break)

- Device ids are platform-opaque: MAC address on Android, `CBPeripheral.identifier` on iOS. The
  known device is reached with `retrievePeripherals(withIdentifiers:)`, not by scanning.
- CoreBluetooth calls `didDisconnectPeripheral` for requested *and* unexpected disconnects.
  `IosBleGattClient` keeps the `errors()` contract by tracking `userRequestedDisconnect` and the
  connections it cancelled itself in `release` (`cancelling`); don't report those.
- Read responses and notifications share one delegate callback; `pendingRead` tells them apart.
- Nothing may be issued before the manager is `poweredOn`; adapter state starts `UNKNOWN`.
  `unauthorized` is not `DISABLED` — authorization goes through `BlePermissionChecker` /
  `rememberBlePermissionController`.
- Delegates are `NSObject` subclasses held strongly by `IosCentral` / the connection handle
  (CoreBluetooth only keeps them weakly). Delegate methods that differ only by parameter name
  need `@ObjCSignatureOverride`.
- `Info.plist` needs `NSBluetoothAlwaysUsageDescription` and
  `CADisableMinimumFrameDurationOnPhone` (Compose Multiplatform checks the latter at start-up).

## Connection model (easy to break)

- One app-scoped GATT connection. **Whoever opens a link closes it**, in `onCleared` via the
  application scope (Koin qualifier `ApplicationScope`; the `viewModelScope` is already cancelled there):
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
- `TrackKnownDeviceUseCase` (app scope, started by `startApp` in `:shared:app`) saves every connection
  that reaches services discovered and merges scan results with the known id. Don't save from
  ViewModels.
- `ScannerViewModel` shows it before any scan and auto-connects **once** per ViewModel via
  `shouldAutoConnect` (Bluetooth ready, idle link). No reconnect loop on the list. Status and copy
  come from `KnownDeviceMapper.kt`; scan results with the known id are excluded via `otherDevices`.
- The details screen falls back to the known device when the scan session lacks it
  (`rssiDbm == null`).
- "Forget device" (scanner card) → `ForgetKnownDeviceUseCase`: disconnects a link to it that is
  open/opening, **then** `KnownDeviceRepository.clear()`. Runs in the application scope. Never touches
  the Android bond. `TrackKnownDeviceUseCase.updateFromScans` must not `filterNotNull()` before
  `combine`, or the last known device lingers and a scan re-saves it after forgetting.

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

- Suites live in `commonTest` and run on the JVM (`testAndroidHostTest`) and on the iOS simulator
  (`iosSimulatorArm64Test`). JVM-only suites (Android constants, the DataStore file test) are in
  `androidHostTest`; iOS mapper tests in `iosTest`; Compose UI tests in `androidDeviceTest`.
- Fakes are shared from `:domain:testing` (`FakeBleGattClient`, `FakeBleGattRepository`,
  `FakeBleScanRepository`, `FakeKnownDeviceRepository`, `MainDispatcherRule`, `assertThat`); no
  test needs BLE hardware. Feature modules get it in `commonTest` from the convention plugin.
- Use `kotlin.test` annotations and the `assertThat` helper from `:domain:testing` (Truth-style,
  multiplatform) — not Truth or JUnit rules, which are JVM-only.
- ViewModel tests install `MainDispatcherRule` (unconfined) from `@BeforeTest`/`@AfterTest`,
  sharing the `runTest` scheduler, so back-off delays and timeouts are driven with `advanceTimeBy`.
- Keep test names as backtick sentences describing behaviour, matching existing suites — without
  commas or other punctuation Kotlin/Native rejects in identifiers.

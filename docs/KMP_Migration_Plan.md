# KMP + Compose Multiplatform Migration Plan

Goal: share the MyBleSense codebase between Android and iOS with Kotlin Multiplatform (KMP) and
Compose Multiplatform (CMP), and ship an iOS app that talks to the same ESP32 POC peripheral.

Verdict: **feasible, and the current architecture is well suited.** The rules in `CLAUDE.md`
(plain-Kotlin `:domain`, Bluetooth types confined to `:core:bluetooth`, pure `*Mapper.kt`
formatting, ViewModels owning state) mean most code moves to `commonMain` with mechanical changes.
The real new work is an iOS Bluetooth client on CoreBluetooth.

> Library versions are not pinned here — check the current Kotlin/CMP/AGP compatibility matrix
> when starting each phase.

---

## 1. Findings: what is shareable today

| Area | Size | Shareable? | Blockers found |
|---|---|---|---|
| `:domain` (models, use cases, `TouchTracker`, `ReconnectPolicy`, sorting, `ScanSession`) | ~1,670 LOC | Yes, almost as-is | `javax.inject.Inject` in 9 files (`usecase/*`, `session/ScanSession.kt`); `java.io.IOException` in `KnownDeviceTest.kt` (test only) |
| `:core:common` | 4 files | Yes | `log/BleLog.kt` uses `android.util.Log`; `format/HexFormat.kt` uses `"%02X".format()` (JVM-only); `di/ApplicationScope.kt`, `di/Dispatchers.kt` use `javax.inject` qualifiers + Hilt |
| `:core:bluetooth` | ~1,500 LOC | Partly | Pure Kotlin, movable to `commonMain`: `gatt/GattOperationQueue.kt`, `mapper/BluetoothSigNames.kt`, `mapper/CompanyIdentifiers.kt`, `mapper/BluetoothUuids.kt`. Android-only: `AndroidBleGattClient`, `AndroidBleScanner`, the repositories, `ScanResultMapper`, `GattMapper`, `ScanFailureMapper`, `AndroidBluetoothStateMonitor`, `BlePermissions`, Hilt modules |
| `:core:datastore` | 3 files | Yes | DataStore Preferences is multiplatform; only the file path and the Hilt module are platform-specific |
| `:core:designsystem` (theme, `BleError.toUserMessage()`) | 7 files | Yes, with CMP | Compose/Material3 → CMP equivalents |
| Feature ViewModels, `*UiState`, `*Mapper.kt` | ~2,000 LOC | Yes | `java.time.Instant`/`DateTimeFormatter` + `Locale` + `String.format` in `DeviceDetailsMapper.kt` and `GattInspectorMapper.kt`; `"%.2f".format` in `StandardCharacteristicDecoder.kt`; `@HiltViewModel`/`@Inject` |
| Feature Composables (`*Screen.kt`, cards, list items) | ~1,600 LOC | Yes, with CMP | `material-icons-extended`, `hiltViewModel()`, `collectAsStateWithLifecycle` (available in CMP lifecycle) |
| Android glue in features | small | No | `permission/BlePermissionEffects.kt` (Activity, `Settings`, `Intent`, `Uri`, `BluetoothAdapter.ACTION_REQUEST_ENABLE`, `ActivityResult`) |
| Navigation (`*Navigation.kt`, `:app`) | small | Yes | Navigation Compose has a multiplatform artifact |
| Tests | 27 suites | Mostly | Turbine and `kotlinx-coroutines-test` are multiplatform. **Truth and JUnit are JVM-only** (`assertThat` in 27 files, `@Rule` in 7, `TemporaryFolder`, `TestWatcher`-based `MainDispatcherRule`) |
| DI | — | No | Hilt is Android-only (`HiltConventionPlugin`, `@HiltAndroidApp`, `@AndroidEntryPoint`, modules) |

Estimated share after migration: **~85–90 %** of the code in `commonMain`.

---

## 2. iOS BLE differences that affect the design

These are not code-porting issues; they change behaviour and must be designed for.

1. **No MAC address.** CoreBluetooth exposes `CBPeripheral.identifier`, a UUID that is stable per
   phone but different on every phone. `KnownDevice.id` keeps working because it is persisted per
   install, but an Android id never matches an iOS id. Document `id` as "platform-opaque".
2. **Reconnecting to the known device** on iOS uses
   `CBCentralManager.retrievePeripherals(withIdentifiers:)` rather than scanning for an address.
   The scanner's known-device auto-connect path needs this in the iOS client.
3. **No bonding API.** iOS pairs implicitly when an encrypted characteristic is accessed and the
   app cannot remove a bond. "Forget device" already never touches the bond, so no change.
4. **No MTU request, no GATT cache refresh.** iOS negotiates MTU itself
   (`maximumWriteValueLength(for:)` reads the result). Any MTU/refresh calls in the client
   interface must be optional/no-op on iOS.
5. **Adapter state is asynchronous.** Nothing may be issued before `centralManagerDidUpdateState`
   reports `poweredOn`. Map `CBManagerState` onto the existing adapter-state flow used by
   `toConnectionStatus`; `unauthorized` maps to the permission-denied state.
6. **No "enable Bluetooth" intent.** iOS shows a system alert automatically
   (`CBCentralManagerOptionShowPowerAlertKey`); the UI can only deep-link to app settings.
7. **Permissions.** One permission (`NSBluetoothAlwaysUsageDescription` in `Info.plist`), prompted
   on first `CBCentralManager` creation. No location permission.
8. **Background.** Scanning in background requires the `bluetooth-central` background mode and
   service-UUID filters (use `PocProfile.SERVICE_UUID`); duplicates are coalesced. Out of scope
   for the first iOS release unless needed.
9. **Disconnect semantics.** `didDisconnectPeripheral` fires for both requested and unexpected
   disconnects (with/without `error`). The iOS client must track "user requested" itself so that
   `BleGattClient.errors()` still emits **only unexpected** losses — `DeviceDetailsViewModel` and
   `ReconnectToDeviceUseCase` depend on that.
10. **Operation queueing.** CoreBluetooth queues internally, but completions arrive via delegate
    callbacks keyed by characteristic. Keep using `GattOperationQueue` so timeouts, error mapping
    and the "one operation at a time" contract are identical on both platforms.
11. **No Bluetooth in the iOS simulator** (same as the Android emulator) — real iPhone + ESP32
    for any BLE testing.

---

## 3. Decisions

| Decision | Choice | Why |
|---|---|---|
| UI | **Compose Multiplatform** | All UI is already Compose + Material3; shares screens, mappers and ViewModels. Alternative (shared logic + SwiftUI via SKIE/KMP-NativeCoroutines) doubles UI work. |
| BLE on iOS | **Own `IosBleGattClient` on CoreBluetooth** via Kotlin/Native platform interop (`platform.CoreBluetooth.*`), implementing the existing `BleGattClient` / `BleScanRepository` interfaces | Keeps the tested Android client and its semantics (`errors()` contract, CCCD handling, queue). Alternative: replace both clients with **Kable** — less code, but discards proven behaviour and the README's GATT lifecycle design. |
| DI | **Koin** (or plain constructor wiring in an `AppGraph`) | Hilt is Android-only. The graph is small; Koin has CMP ViewModel support. |
| Dates/time | **kotlinx-datetime** | Replaces `java.time` in mappers. |
| Number/hex formatting | Hand-written common helpers in `:core:common` | `String.format` is unavailable in common code. |
| Tests | **kotlin.test** asserts in `commonTest`; keep Turbine | Truth/JUnit are JVM-only. Compose UI tests stay Android instrumented tests initially. |
| Logging | `expect object BleLog` (Logcat on Android, `NSLog`/`os_log` on iOS) or Kermit | Removes `android.util.Log` from common code. |
| Android KMP library plugin | `com.android.kotlin.multiplatform.library` | AGP 9 requires it for KMP library modules; replaces `com.android.library` in the convention plugins. |

---

## 4. Migration phases

Each phase leaves the Android app shippable. After every phase run:

```bash
export JAVA_HOME=~/Library/Java/JavaVirtualMachines/jbr-21.0.11/Contents/Home
./gradlew test assembleDebug lintDebug
```

### Phase 0 — Prerequisites

- Install Xcode (currently missing: `xcodebuild` reports "requires Xcode, but active developer
  directory '/Library/Developer/CommandLineTools'"), then
  `sudo xcode-select -s /Applications/Xcode.app`.
- Apple Developer account for signing on a physical iPhone.
- Confirm a Kotlin / Compose Multiplatform / AGP 9 combination that is officially compatible.

### Phase 1 — Build logic + `:domain` + `:core:common` to KMP (Android target only)

- `build-logic`: add convention plugins
  - `myblesense.kmp.library` — `kotlin("multiplatform")` + `com.android.kotlin.multiplatform.library`,
    `androidTarget`/`android {}` config, JVM toolchain 17, later iOS targets.
  - `myblesense.kmp.compose` — adds `org.jetbrains.compose` + compose compiler.
  - Keep `myblesense.android.application` for `:app`. Retire `myblesense.jvm.library` once
    `:domain` is converted.
- `:domain`: move `src/main` → `src/commonMain`, `src/test` → `src/commonTest`,
  `src/testFixtures` → a `commonTest`-visible location (KMP has no `testFixtures`; use a small
  `:domain:testing` KMP module consumed by other modules' `commonTest`).
  - Delete `@Inject`/`@Singleton`/`@Qualifier` from use cases and `ScanSession` (constructors stay;
    DI wiring moves to modules in Phase 2).
  - Replace `IOException` in `KnownDeviceTest` with a local test exception.
- `:core:common`:
  - `HexFormat.kt`: replace `"%02X".format(it)` / `"0x%04X".format(this)` with a common helper
    (e.g. `toString(16).uppercase().padStart(n, '0')`, or Kotlin's `HexFormat` API).
  - `BleLog`: `expect`/`actual` (Android → `android.util.Log`, iOS → `NSLog`).
  - `di/ApplicationScope.kt`, `di/Dispatchers.kt`: drop `javax.inject` qualifiers; expose the app
    `CoroutineScope` and dispatchers through the new DI (Koin named qualifiers).
- Tests: Truth `assertThat(x).isEqualTo(y)` → `assertEquals(y, x)` etc.; `MainDispatcherRule`
  (JUnit `TestWatcher`) → a helper that calls `Dispatchers.setMain`/`resetMain` from
  `@BeforeTest`/`@AfterTest`, still sharing the `runTest` scheduler.

### Phase 2 — DI swap (Hilt → Koin) and data layer

- Replace `HiltConventionPlugin`, `@HiltAndroidApp` (`MyBleSenseApplication`),
  `@AndroidEntryPoint` (`MainActivity`), `@HiltViewModel` + `hiltViewModel()` with Koin modules
  and `koinViewModel()`.
  - `domainModule` (use cases), `commonModule` (`@ApplicationScope` scope, dispatchers),
    `dataStoreModule`, `bluetoothModule` (platform-specific `actual` module).
  - `TrackKnownDeviceUseCase` still starts at app start (Android `Application.onCreate`,
    iOS app init).
- `:core:datastore` → KMP: DataStore in `commonMain`; `expect fun dataStorePath(): String`
  (Android `context.filesDir`, iOS `NSDocumentDirectory`). Still strings only.
- `:core:bluetooth` → KMP with `androidMain` holding all current Android code. Move to
  `commonMain`: `GattOperationQueue`, `BluetoothSigNames`, `CompanyIdentifiers`, `BluetoothUuids`
  (and their tests). Keep `ScanFailureMapper`, `ScanResultMapper`, `GattMapper` in `androidMain`.

### Phase 3 — Features + design system to Compose Multiplatform

- `:core:designsystem`, `:feature:scanner`, `:feature:device-details`, `:feature:gatt-inspector`
  → `myblesense.kmp.compose`; move code to `commonMain`.
  - Compose imports: `androidx.compose.*` keep the same package names in CMP; swap the
    dependencies to `compose.runtime`, `compose.foundation`, `compose.material3`,
    `compose.materialIconsExtended` (or replace extended icons with vector resources if the
    artifact is unavailable).
  - ViewModels: JetBrains `lifecycle-viewmodel` / `lifecycle-runtime-compose` (same API,
    including `SavedStateHandle` and `viewModelScope`).
  - Mappers: `java.time.Instant` + `DateTimeFormatter` → `kotlinx.datetime.Instant` +
    `toLocalDateTime(TimeZone)`, formatting `HH:mm:ss.SSS` by hand (keep the fixed-width
    timestamp column behaviour). `String.format(Locale.US, "%.1fs", …)` and `"%.2f %s"` → a common
    `formatDecimal(value, digits)` helper in `:core:common` (no locale-dependent separators — keep
    the dot).
  - `BlePermissionEffects.kt` → `expect` composable `BlePermissionEffects(...)`: Android `actual`
    is today's file; iOS `actual` reads `CBManager.authorization` and links to
    `UIApplicationOpenSettingsURLString`.
  - String resources (if any move out of Kotlin) → CMP `composeResources`.
- Navigation: JetBrains `navigation-compose` in a shared `:shared:app` (or `:composeApp`) module
  that builds the nav graph currently in `:app`; `:app` becomes a thin Android shell
  (`MainActivity` → `setContent { App() }`).
- Keep the M3 workarounds in `CLAUDE.md` (intensity bar `drawStopIndicator = {}`,
  `gapSize = 0.dp`, explicit track colour) and verify they still apply on the CMP Material3
  version.

### Phase 4 — iOS target and CoreBluetooth client

- Enable `iosArm64()` + `iosSimulatorArm64()` in the KMP convention plugin; produce a framework
  (`baseName = "Shared"`, static) from the shared app module.
- `:core:bluetooth` `iosMain`:
  - `IosBluetoothStateMonitor` — `CBCentralManagerDelegate.centralManagerDidUpdateState` →
    adapter-state `StateFlow`.
  - `IosBleScanner` / `IosBleScanRepository` — `scanForPeripheralsWithServices`, map
    `advertisementData` (local name, manufacturer data, service data, RSSI) to `BleDevice`;
    `id = peripheral.identifier.UUIDString`.
  - `IosBleGattClient` — connect/disconnect, `discoverServices` → `discoverCharacteristics` →
    `discoverDescriptors`, read/write/`setNotifyValue`, all through `GattOperationQueue`;
    map `NSError` → `BleError`; track user-requested disconnects so `errors()` emits only
    unexpected losses; same `connection` `StateFlow` contract as the Android client.
  - Delegates are `NSObject` subclasses implementing `CBCentralManagerDelegateProtocol` /
    `CBPeripheralDelegateProtocol`; bridge callbacks into `CompletableDeferred`/`Channel`s on a
    single dispatch queue.
  - Known device: `retrievePeripheralsWithIdentifiers` for auto-connect without a scan.
- `iosApp/` Xcode project: SwiftUI `@main` app hosting `ComposeUIViewController { App() }`,
  `Info.plist` with `NSBluetoothAlwaysUsageDescription`, Koin start + `TrackKnownDeviceUseCase`
  start in app init.

### Phase 5 — Verification on hardware

- Android: full regression on the ESP32 board (scan, known-device auto-connect, details,
  touch events/intensity bar, GATT inspector, forget device, reconnect back-off).
- iOS: same checklist on a physical iPhone. Specific checks:
  - `errors()` does not emit on user disconnect; reconnect is bounded (3 attempts, 1/2/4 s).
  - POC Events CCCD re-subscribed after every reconnection; disabled before explicit disconnect.
  - Known device survives app restart and auto-connects once per scanner ViewModel.
  - `TOUCH_ON` / `TOUCH_INTENSITY` / `TOUCH_OFF` timing matches Android (notification arrival
    times drive duration).
- Update `README.md` and `CLAUDE.md` (module layout, new build/test commands, iOS run steps,
  platform-opaque device id).

---

## 5. Target module layout

```
:domain               KMP  commonMain (unchanged rules: no platform, no Compose)
:domain:testing       KMP  shared fakes (FakeBleGattClient, …) + main-dispatcher helper
:core:common          KMP  commonMain + expect/actual BleLog
:core:bluetooth       KMP  commonMain (queue, SIG tables) | androidMain | iosMain
:core:datastore       KMP  commonMain + expect path
:core:designsystem    KMP + CMP
:feature:*            KMP + CMP  (still never depend on each other)
:shared:app           KMP + CMP  nav graph, Koin start, App() root composable
:app                  Android application shell
iosApp/               Xcode project hosting the shared framework
```

The architecture rules in `CLAUDE.md` stay valid; "Android Bluetooth/GATT types never leave
`:core:bluetooth`" becomes "platform Bluetooth types (Android or CoreBluetooth) never leave
`:core:bluetooth`'s platform source sets".

---

## 6. Risks

| Risk | Mitigation |
|---|---|
| iOS disconnect/reconnect semantics diverge from Android and break the `errors()` contract | Port the existing `AndroidBleGattClient` behaviour tests as contract tests against `BleGattClient`; run them against both fakes and a manual hardware checklist |
| AGP 9 + KMP plugin churn | Do Phase 1 on a branch with only build changes; verify Android before moving code |
| CMP Material3 lags AndroidX Material3 | Check the components used (`LinearProgressIndicator` params, icons) before Phase 3 |
| Compose UI instrumented tests (Espresso) are Android-only | Keep them in `androidInstrumentedTest`; consider CMP `runComposeUiTest` in `commonTest` later |
| Large test rewrite (Truth → kotlin.test) | Mechanical; can be done per module, or temporarily keep suites in `androidUnitTest` |

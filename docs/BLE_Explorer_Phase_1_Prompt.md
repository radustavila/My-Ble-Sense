# BLE Explorer — Phase 1 Claude CLI Prompt

You are implementing **Phase 1** of an Android portfolio project called **BLE Explorer**.

The goal of Phase 1 is to build a clean, modern, multi-module Android application that discovers nearby Bluetooth Low Energy devices and displays their advertising information.

This phase is intentionally limited to **BLE discovery/scanning**.

Do NOT implement GATT connections, characteristic read/write, notifications, pairing, or device control yet. Those belong to Phase 2.

The project should demonstrate modern Android engineering, Clean Architecture, multi-module architecture, Kotlin Coroutines/Flow, dependency injection, lifecycle handling, permissions, and testability.

---

# 1. First: inspect the existing project

Before changing anything:

1. Inspect the complete existing project structure.
2. Inspect all Gradle files and version catalogs.
3. Inspect the existing Kotlin/Android configuration.
4. Determine the currently configured:
   - compileSdk
   - targetSdk
   - minSdk
   - Kotlin version
   - AGP version
   - Compose version
   - Java/JDK version
5. Reuse the existing setup where possible.
6. Use the latest stable Android technologies compatible with the existing project.
7. Do NOT blindly upgrade unrelated dependencies just because newer versions exist.
8. Do NOT overwrite unrelated existing work.

If the repository is empty, create the project from scratch using a modern Android setup.

---

# 2. Architecture

Use **Clean Architecture + Multi-Module Architecture**.

The project should have clear boundaries between presentation, domain, and data/infrastructure.

Suggested structure:

```text
:app

:core
    :core:common
    :core:bluetooth
    :core:designsystem

:domain

:feature
    :feature:scanner
    :feature:device-details
```

You may adjust the exact structure if there is a strong architectural reason, but keep the project genuinely modular.

Avoid creating modules without a meaningful responsibility.

---

# 3. Module responsibilities

## :app

Responsible for:

- Application entry point
- Hilt initialization
- Top-level navigation
- Connecting feature modules
- Application-level configuration

Do not put BLE implementation here.

---

## :core:bluetooth

Responsible for Android BLE infrastructure.

Contains:

- Bluetooth capability detection
- Bluetooth adapter access
- BLE scanner implementation
- Android Bluetooth APIs
- BLE scan callbacks
- conversion of Android callbacks into Flow
- scan configuration
- advertising data parsing

This module may depend on Android Bluetooth APIs.

It must NOT contain UI code.

---

## :core:common

Contains genuinely shared utilities and abstractions.

Keep this module small.

Do not turn it into a dumping ground.

---

## :core:designsystem

Contains:

- Material 3 theme
- typography
- shared Compose components
- reusable UI primitives

Keep feature-specific UI inside feature modules.

---

## :domain

Contains:

- domain models
- repository interfaces
- use cases
- business rules

The domain module must NOT depend on Android Bluetooth APIs.

It must not know about:

- `BluetoothDevice`
- `BluetoothLeScanner`
- `ScanCallback`
- Android Context
- Activity
- Fragment
- Compose
- Retrofit
- Room

The domain layer should remain platform-independent.

---

## :feature:scanner

Responsible for:

- BLE scanner screen
- scanner ViewModel
- scanner UI state
- scanner-specific events
- scanner-specific presentation logic

---

## :feature:device-details

Responsible for:

- device details screen
- device details UI
- displaying advertising information

Do NOT implement GATT connection here yet.

---

# 4. BLE functionality

Implement BLE scanning using Android's modern Bluetooth LE APIs.

Use:

```kotlin
BluetoothManager
BluetoothAdapter
BluetoothLeScanner
ScanCallback
ScanResult
ScanRecord
```

where appropriate.

Do not use deprecated Bluetooth APIs unless required for backwards compatibility.

---

# 5. BLE capability detection

At application/screen level determine:

1. Does the device support Bluetooth?
2. Does it support Bluetooth LE?
3. Is Bluetooth currently enabled?
4. Are the required runtime permissions granted?

Represent these states explicitly.

Example:

```text
BLE supported
Bluetooth enabled
Permissions granted
Ready to scan
```

If BLE is unsupported:

```text
Bluetooth LE is not supported on this device.
```

If Bluetooth is disabled:

```text
Bluetooth is disabled.

[ Enable Bluetooth ]
```

Use the appropriate Android mechanism to request enabling Bluetooth.

Do not crash if the user refuses.

---

# 6. Permissions

Handle BLE runtime permissions correctly for the supported Android versions.

For modern Android versions, account for:

```text
BLUETOOTH_SCAN
BLUETOOTH_CONNECT
```

Also handle the permission model for older Android versions supported by the project.

Do not request unnecessary permissions.

The permission flow should be:

```text
App opened
    ↓
Check permissions
    ↓
Missing?
    ↓
Request
    ↓
Granted?
 ┌──────┴──────┐
YES           NO
 ↓             ↓
Ready       Explanation
```

If permissions are denied, provide a useful explanation and retry/settings path where appropriate.

Do not repeatedly request a permission after the user has permanently denied it.

---

# 7. BLE scanner abstraction

Create a clean abstraction.

For example:

```kotlin
interface BleScanner {
    fun scan(): Flow<BleScanResult>
    fun stopScan()
}
```

Use an appropriate model for scan results.

The Android implementation should wrap `BluetoothLeScanner`.

Convert callback-based APIs into Kotlin Flow.

Do not expose `ScanCallback` outside the infrastructure layer.

Do not expose `BluetoothDevice` to the domain or UI layers.

---

# 8. Domain model

Create a domain-level BLE device model.

For example:

```kotlin
data class BleDevice(
    val id: String,
    val name: String?,
    val rssi: Int,
    val address: String?,
    val serviceUuids: List<String>,
    val manufacturerData: List<ManufacturerData>,
    val serviceData: List<ServiceData>,
    val txPower: Int?
)
```

Adjust the model based on what Android actually exposes.

Do not blindly copy Android framework classes into the domain model.

Create appropriate domain models for advertising information.

---

# 9. Advertising data

Extract as much useful information as is safely available from `ScanRecord`.

Support:

- device name
- service UUIDs
- manufacturer-specific data
- service data
- TX power where available

Do not attempt to reverse-engineer proprietary manufacturer payloads.

Display raw hexadecimal payloads where appropriate.

Create reusable parsing/mapping logic instead of parsing advertising data inside Compose.

---

# 10. Duplicate handling

BLE scanning can report the same device repeatedly.

The application must display one entry per device.

When a known device is discovered again:

- update its RSSI
- update advertising information if it changed
- do not add another list item

Use a stable identifier appropriate for the Android BLE environment.

Do not assume the device name is unique.

---

# 11. Scanner use case

Create a domain use case such as:

```kotlin
StartBleScanUseCase
```

or another appropriately named abstraction.

The ViewModel should depend on the use case/repository abstraction rather than directly on `BluetoothLeScanner`.

Example flow:

```text
Compose
   ↓
ViewModel
   ↓
UseCase
   ↓
Repository
   ↓
BleScanner
   ↓
Android Bluetooth APIs
```

---

# 12. Scan lifecycle

Scanning must NEVER continue indefinitely.

Implement a scan duration of approximately:

```text
15 seconds
```

Make the duration configurable where appropriate.

Stop scanning when:

- user taps Stop
- timeout is reached
- screen lifecycle requires it
- Bluetooth becomes disabled
- required permissions are revoked
- an unrecoverable scanner error occurs

Do not leak callbacks.

Do not leave BLE scanning running after the relevant lifecycle ends.

Use structured concurrency.

Do not use `GlobalScope`.

---

# 13. Scanner UI

Create a polished but intentionally simple Material 3 UI.

Main screen:

```text
BLE Explorer

Scanning...

🟢 Venu Sq
   Garmin
   -52 dBm

🟢 Samsung TV
   -67 dBm

🟢 Unknown Device
   -81 dBm
```

The exact devices will depend on the physical environment.

Provide:

- Start Scan
- Stop Scan
- scan progress/state
- number of devices found
- device list
- empty state
- permission state
- Bluetooth disabled state
- BLE unsupported state
- error state

Do not hardcode specific devices.

---

# 14. Device list

Each device row should display:

- device name or "Unknown device"
- RSSI
- manufacturer if it can be safely determined
- useful secondary advertising information

Make the list reactive.

RSSI changes should update the existing device rather than creating new rows.

Use stable Compose keys.

---

# 15. RSSI presentation

Display RSSI in dBm.

Optionally provide a simple qualitative indicator:

```text
Strong
Medium
Weak
```

Do NOT present RSSI as an exact distance measurement.

Do not claim:

```text
5 meters away
```

based solely on RSSI.

---

# 16. Sorting

Implement simple sorting.

At minimum:

- signal strength
- name

Use a Material 3 menu/dropdown or another appropriate UI.

Keep sorting logic out of Composables where possible.

---

# 17. Device details screen

When the user taps a device, navigate to:

```text
Device Details
```

Display:

```text
Name
Venu Sq

RSSI
-52 dBm

Address
XX:XX:XX:XX

Service UUIDs
1800
1801
...

Manufacturer Data
Company ID: XXXX
Payload: ...

Service Data
...

TX Power
...
```

Use a proper state model.

Handle missing values gracefully.

For privacy, consider masking the MAC address in the UI by default while still allowing a debug/details representation if appropriate.

---

# 18. IMPORTANT: Phase 1 scope

Do NOT implement:

- GATT connection
- `connectGatt`
- service discovery after connection
- characteristic read
- characteristic write
- characteristic notifications
- pairing
- bonding
- BLE peripheral/server mode
- ESP32 integration
- Garmin Connect IQ integration
- device control

Those belong to later phases.

Phase 1 is strictly:

```text
BLE capability
+
permissions
+
scanning
+
advertising data
+
deduplication
+
RSSI
+
device details
+
lifecycle management
```

---

# 19. Bluetooth state changes

Observe Bluetooth adapter state.

If Bluetooth changes from enabled → disabled while scanning:

1. stop the scan
2. update UI state
3. display appropriate message

If Bluetooth becomes enabled again:

Do not automatically start scanning unless that behavior is explicitly justified.

Prefer requiring the user to start scanning again.

---

# 20. Error handling

Create a clean BLE error abstraction.

Handle cases such as:

- BLE unsupported
- Bluetooth disabled
- permission denied
- scanner unavailable
- scan already started
- scan failed
- Bluetooth state changed
- unexpected Android BLE errors

Do not expose raw Android exceptions directly to Compose.

Map infrastructure errors to domain/application errors.

---

# 21. State management

Use immutable UI state and unidirectional data flow.

Example:

```kotlin
data class ScannerUiState(
    val devices: List<BleDevice> = emptyList(),
    val isScanning: Boolean = false,
    val bluetoothState: BluetoothState = BluetoothState.Unknown,
    val permissionState: PermissionState = PermissionState.Unknown,
    val error: BleError? = null
)
```

Use `StateFlow`.

User actions should flow through the ViewModel.

Avoid mutable state scattered across multiple classes.

---

# 22. Testing

Testing is required.

Create a fake implementation of the BLE scanner:

```kotlin
FakeBleScanner
```

It should allow deterministic test data to be emitted.

Test at minimum:

### Scanner/domain tests

- duplicate device handling
- RSSI updates
- new device insertion
- scan start
- scan stop
- timeout behavior
- error mapping
- sorting
- missing device names
- advertising data mapping

### ViewModel tests

Test:

```text
Initial state
Start scan
Devices discovered
Existing device RSSI updated
Stop scan
Scan timeout
Bluetooth disabled
Permission denied
Error
Retry
```

Do not depend on physical Bluetooth hardware for unit tests.

---

# 23. Hardware verification

After implementation, run the application on a real Android device.

Verify:

1. permissions work
2. Bluetooth disabled state works
3. BLE scan starts
4. nearby BLE devices appear
5. RSSI updates
6. duplicates are not created
7. scan automatically stops after timeout
8. manual Stop works
9. tapping a device opens details
10. advertising information is displayed

There may be no guarantee that the Garmin Venu Sq or TV will advertise discoverable BLE data in a way visible to the application.

Do NOT modify the implementation merely because a particular consumer device does not appear.

The application should work with any discoverable BLE peripherals.

---

# 24. Logging

Add useful development logging around BLE operations, but avoid excessive logs.

Examples:

```text
BLE scan started
BLE device discovered
BLE device updated
BLE scan stopped
BLE scan failed
Bluetooth state changed
```

Do not log sensitive data unnecessarily.

Avoid logging complete raw payloads in production builds unless appropriate.

---

# 25. Code quality

Follow modern Android best practices.

Rules:

- no Bluetooth calls from Composables
- no Android Bluetooth classes in domain
- no business logic in Composables
- no `GlobalScope`
- no memory leaks
- no unbounded scanning
- no unnecessary abstractions
- no circular module dependencies
- constructor injection
- immutable state
- lifecycle-aware Flow collection
- structured concurrency
- clear module boundaries

Prefer simple, maintainable architecture over over-engineering.

---

# 26. Documentation

Create/update the README.

Document:

- project purpose
- architecture
- module structure
- dependency graph
- BLE scanning flow
- permission handling
- advertising data parsing
- duplicate handling
- lifecycle strategy
- testing strategy
- Phase 1 scope
- Phase 2 planned functionality

Include Mermaid diagrams where useful.

For example:

```text
:app
 ├── :feature:scanner
 ├── :feature:device-details
 ├── :domain
 └── :core:bluetooth
        └── Android BLE APIs
```

Also include an **Engineering Highlights** section suitable for a portfolio/interview.

---

# 27. Final verification

After implementation:

1. Build the complete project.
2. Run unit tests.
3. Run lint/static analysis if configured.
4. Fix compilation errors.
5. Fix test failures.
6. Verify module dependency boundaries.
7. Verify no deprecated BLE APIs are unnecessarily used.
8. Verify lifecycle cleanup.
9. Verify permission behavior.
10. Verify the main UI flows on a real Android device.

Do not stop after creating the architecture.

Actually implement the complete Phase 1 functionality.

At the end, provide:

### Implementation summary
- modules created/modified
- important classes
- BLE architecture
- permission strategy
- lifecycle strategy
- tests added

### Verification
- build result
- test result
- lint result
- hardware verification result

### Known limitations
Clearly mention anything that could not be verified on physical hardware.

Do not implement Phase 2 functionality.

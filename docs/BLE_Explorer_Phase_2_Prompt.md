# BLE Explorer — Phase 2 Claude CLI Implementation Prompt

You are working on an Android project called **BLE Explorer**.

Phase 1 implemented BLE discovery/scanning. Phase 2 should extend the project into a real **BLE GATT client / GATT Inspector**.

## 1. First: inspect the existing project

Before making changes:

- Inspect the existing module structure.
- Inspect the current BLE abstractions and implementations.
- Inspect domain models, repositories, use cases, ViewModels, UI state and navigation.
- Inspect the existing permission handling and Bluetooth state handling.
- Inspect existing tests.
- Reuse and extend the Phase 1 architecture rather than creating parallel implementations.
- Do not blindly rewrite existing code.
- Preserve working Phase 1 functionality.

If anything is unclear or inconsistent with the requirements below, ask questions before proceeding.

---

# 2. Goal of Phase 2

Transform BLE Explorer from a BLE scanner into a generic **BLE GATT Inspector**.

The main flow should be:

```text
Scan
  ↓
Select BLE device
  ↓
Connect
  ↓
Discover GATT services
  ↓
Inspect services
  ↓
Inspect characteristics
  ↓
Read characteristics
  ↓
Write characteristics where supported
  ↓
Enable/disable notifications where supported
  ↓
Display incoming notification values
```

The implementation must remain generic.

Do NOT build Garmin-specific logic into the core architecture.

---

# 3. Technology requirements

Use modern Android development practices:

- Kotlin
- Jetpack Compose
- Material 3
- Coroutines
- Flow / StateFlow
- Hilt
- Clean Architecture
- Multi-module architecture
- Lifecycle-aware collection
- Immutable UI state
- Unidirectional data flow where appropriate
- Android Bluetooth LE APIs

Use the existing project versions and conventions where possible.

Do not introduce unnecessary libraries.

---

# 4. Architecture

Keep the existing module structure from Phase 1 and extend it appropriately.

Expected structure:

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
    :feature:gatt-inspector
```

If the existing project has a slightly different but valid structure, preserve it rather than restructuring purely for cosmetic reasons.

## Responsibilities

### `:app`

Responsible for:

- Application class
- Hilt setup
- Top-level navigation
- Application configuration

It must NOT contain BLE implementation details.

### `:core:bluetooth`

Responsible for Android BLE infrastructure:

- `BluetoothManager`
- `BluetoothAdapter`
- `BluetoothGatt`
- `BluetoothGattCallback`
- GATT connection management
- Service discovery
- Characteristic operations
- Descriptor operations required for notifications
- Mapping Android BLE callbacks into application/domain-friendly models
- BLE-specific error handling
- GATT operation serialization/queue
- Conversion of callback-based APIs into Flow/suspend APIs where appropriate

Do not expose Android framework BLE classes such as `BluetoothGatt`, `BluetoothGattService` or `BluetoothGattCharacteristic` outside this infrastructure layer.

### `:domain`

Contains:

- Domain models
- Repository interfaces
- Use cases
- Business rules

The domain layer must not depend on:

- Android Bluetooth APIs
- Compose
- `BluetoothGatt`
- `BluetoothDevice`
- Android UI classes

### `:feature:gatt-inspector`

Responsible for:

- GATT Inspector UI
- ViewModel
- UI state
- UI events
- Displaying services
- Displaying characteristics
- Read/write/notification actions
- Connection state presentation

---

# 5. GATT domain model

Create clean domain models.

For example:

```kotlin
data class GattService(
    val uuid: String,
    val characteristics: List<GattCharacteristic>
)

data class GattCharacteristic(
    val uuid: String,
    val properties: Set<GattCharacteristicProperty>,
    val value: ByteArray? = null
)

enum class GattCharacteristicProperty {
    READ,
    WRITE,
    WRITE_NO_RESPONSE,
    NOTIFY,
    INDICATE
}
```

You may adjust the exact models if needed.

Avoid leaking Android-specific types into the domain.

Byte arrays should remain raw bytes unless the characteristic is explicitly known to use a standard format.

---

# 6. GATT connection abstraction

Create a clean abstraction around GATT.

For example:

```kotlin
interface BleGattClient {

    fun connectionState(): Flow<GattConnectionState>

    fun services(): Flow<List<GattService>>

    suspend fun connect(deviceId: String)

    suspend fun disconnect()

    suspend fun readCharacteristic(
        serviceUuid: String,
        characteristicUuid: String
    ): Result<ByteArray>

    suspend fun writeCharacteristic(
        serviceUuid: String,
        characteristicUuid: String,
        value: ByteArray
    ): Result<Unit>

    fun observeCharacteristic(
        serviceUuid: String,
        characteristicUuid: String
    ): Flow<ByteArray>
}
```

The exact API can differ, but keep responsibilities clear.

Do not force a specific API if the existing architecture has a better abstraction.

---

# 7. Connection lifecycle

Implement explicit connection states.

For example:

```kotlin
enum class GattConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}
```

Errors should be represented separately rather than abusing connection states.

Handle:

- Connecting
- Connected
- Disconnecting
- Disconnected
- Connection failure
- Device out of range
- GATT errors
- Bluetooth disabled during connection
- Permissions revoked
- App lifecycle termination
- Explicit user disconnect

When the connection is no longer needed:

- close the GATT connection
- unregister callbacks/flows as appropriate
- release resources
- avoid leaking `BluetoothGatt`

---

# 8. Service discovery

After a successful connection:

```kotlin
discoverServices()
```

Expose the discovered services through a reactive mechanism.

Map Android GATT objects into domain models.

Example:

```text
Services

Generic Access
1800

Generic Attribute
1801

Battery Service
180F
```

Each service should be expandable.

---

# 9. Characteristics

For each service show its characteristics.

Example:

```text
Battery Service
────────────────────

Battery Level
UUID: 2A19

Properties:
✓ READ
✓ NOTIFY
```

Show:

- UUID
- human-readable name where it can be safely determined
- properties
- current/latest value when available

Do not assume a UUID has a human-readable name unless a standard UUID mapping is available.

For unknown UUIDs, display the UUID itself.

---

# 10. Read characteristic

If a characteristic supports `READ`, expose a Read action.

Example:

```text
Battery Level

Properties
READ

Value
76%

[ READ ]
```

The underlying operation must use:

```text
BluetoothGatt.readCharacteristic(...)
```

Handle:

- success
- failure
- timeout
- invalid characteristic
- disconnected device
- GATT error

Return raw bytes from the infrastructure layer.

If the characteristic is a known standard characteristic, the UI may optionally show a human-readable interpretation in addition to the raw bytes.

Example:

```text
Raw
4C

Decoded
76%
```

Do not hardcode arbitrary device-specific decoding.

---

# 11. Write characteristic

If a characteristic supports:

- `WRITE`
- `WRITE_NO_RESPONSE`

provide a Write UI.

Example:

```text
Characteristic

UUID
XXXX

Properties
WRITE

Value (HEX)

[ 01 FF 2A ]

[ WRITE ]
```

Requirements:

- Accept hexadecimal input.
- Validate malformed input.
- Convert HEX → ByteArray.
- Perform the correct GATT operation.
- Display success/failure.
- Do not crash on invalid input.
- Respect whether the characteristic supports write-with-response or write-without-response.

Do not send writes automatically.

The user must explicitly initiate them.

---

# 12. Notifications

For characteristics supporting:

- `NOTIFY`
- `INDICATE`

provide an Enable/Disable notification action.

Example:

```text
Heart Rate Measurement

Properties
NOTIFY

Notifications
OFF

[ Enable notifications ]
```

When enabled:

```text
Notifications
ON

12:31:04
48 00

12:31:05
4A 00

12:31:06
4C 00
```

Requirements:

- Configure the appropriate Client Characteristic Configuration Descriptor (CCCD).
- Enable notifications/indications using the appropriate GATT APIs.
- Expose incoming values as `Flow<ByteArray>`.
- Allow disabling notifications.
- Stop collecting/observing when the connection is gone.
- Handle errors.

Keep notification data raw by default.

If a standard characteristic has an obvious standard representation, an optional decoded value may be shown.

---

# 13. GATT operation queue

This is an important part of Phase 2.

BLE GATT operations should not be fired concurrently without coordination.

Implement a serialized operation mechanism such as:

```text
GattOperationQueue

READ
  ↓
WRITE
  ↓
ENABLE_NOTIFICATION
  ↓
READ
```

Only one operation should be actively waiting for its corresponding callback at a time where Android's GATT behavior requires serialization.

The queue should:

- serialize operations
- resume/complete the corresponding coroutine when the callback arrives
- handle failure
- handle timeout
- handle disconnect
- avoid deadlocks
- clean up pending operations when the GATT connection closes

Do not implement a naive queue that can become permanently blocked after one failed operation.

Document the reasoning in code comments where useful.

---

# 14. Callback → Flow conversion

Android GATT is callback based.

Do not expose callback APIs to the UI layer.

Convert callbacks into appropriate reactive abstractions.

Example:

```text
BluetoothGattCallback
        ↓
core:bluetooth
        ↓
Flow
        ↓
Repository
        ↓
UseCase
        ↓
ViewModel
        ↓
Compose
```

Use:

- `StateFlow` for state
- `Flow` for streams/events
- `suspend` functions for one-shot operations where appropriate

Make sure callback registration and cleanup are lifecycle-safe.

---

# 15. Device Details integration

Phase 1 already has a Device Details screen.

Extend it so that after selecting a BLE device the user can:

```text
Device Details

Name
Venu Sq

RSSI
-52 dBm

Address
XX:XX:XX:XX

[ Connect ]
```

After connection:

```text
🟢 Connected

[ Disconnect ]

[ Open GATT Inspector ]
```

Do not hardcode Garmin or Samsung.

The selected device must come from the scanner.

---

# 16. GATT Inspector UI

Create a dedicated screen.

Suggested structure:

```text
GATT Inspector

🟢 Connected

Services
────────────────────

▼ Battery Service
  180F

  Battery Level
  2A19

  READ
  NOTIFY

  Value:
  76%

  [ READ ]
  [ Enable notifications ]


▼ Device Information
  180A

  Manufacturer Name
  2A29

  READ

  [ READ ]
```

The UI should work with:

- standard services
- unknown services
- standard characteristics
- unknown characteristics

Do not assume a particular device layout.

---

# 17. Raw data presentation

For raw BLE values:

- display hexadecimal representation
- optionally display byte count
- optionally display UTF-8 text when decoding is safe/useful
- never silently reinterpret arbitrary binary payloads

Example:

```text
Raw
4C 00 7F

Bytes
3
```

If decoding is shown:

```text
UTF-8
L
```

make it clear that this is an interpretation and may not be meaningful for arbitrary binary data.

---

# 18. Standard UUID handling

Implement a small UUID utility/mapping for commonly used Bluetooth SIG services/characteristics where useful.

For example:

```text
1800 → Generic Access
1801 → Generic Attribute
180F → Battery Service
180A → Device Information
2A19 → Battery Level
```

Keep this mapping isolated from the core GATT implementation.

Unknown UUIDs should still work normally.

Do not build a huge UUID database unless there is a strong reason.

---

# 19. Error handling

Create meaningful domain/application errors.

Potential errors:

```text
BluetoothDisabled
PermissionDenied
DeviceNotFound
ConnectionFailed
ConnectionTimeout
Disconnected
ServiceDiscoveryFailed
CharacteristicNotFound
CharacteristicReadFailed
CharacteristicWriteFailed
NotificationEnableFailed
GattOperationTimeout
UnknownGattError
```

The UI should display useful messages without exposing low-level implementation details.

---

# 20. Bluetooth and permission changes

Reuse Phase 1 permission and Bluetooth-state handling.

If Bluetooth becomes disabled while connected:

- stop operations
- close the GATT connection
- update connection state
- show an appropriate message

If permissions are revoked:

- stop BLE operations
- update UI state
- request permissions again when appropriate

Do not automatically start scanning or reconnecting indefinitely.

---

# 21. Lifecycle and resource management

This is critical.

Make sure:

- GATT is closed when no longer needed.
- Connections do not survive unintentionally after leaving the relevant flow.
- Compose recomposition does not create multiple GATT connections.
- ViewModel recreation does not accidentally create duplicate callbacks.
- Notification collectors are cleaned up.
- Pending GATT operations are completed/cancelled when the connection closes.
- No Android Context or GATT object is unnecessarily retained by UI/domain code.

---

# 22. Testing

Do not rely on physical BLE hardware for unit tests.

Create fakes such as:

```kotlin
FakeBleGattClient
```

Test:

### Connection

- initial disconnected state
- connect
- connecting state
- connected state
- disconnect
- connection failure
- timeout
- unexpected disconnect

### Service discovery

- services received
- empty services
- discovery failure
- unknown UUIDs

### Read

- read success
- read failure
- timeout
- disconnected state

### Write

- valid HEX input
- invalid HEX input
- write success
- write failure
- write without response

### Notifications

- enable notification
- disable notification
- incoming values
- multiple incoming values
- notification failure
- disconnect while observing notifications

### Operation queue

Test:

- operations execute sequentially
- second operation waits for first
- first operation fails and queue continues
- timeout does not deadlock the queue
- disconnect clears pending operations

### ViewModel

Test state transitions and user events.

Do not make tests dependent on a Garmin watch, Samsung TV, or any specific physical device.

---

# 23. Physical device verification

After implementation, verify on a real Android phone.

Test at minimum:

1. Open BLE Explorer.
2. Scan.
3. Select a BLE device.
4. Open Device Details.
5. Connect.
6. Verify connection state.
7. Discover services.
8. Open GATT Inspector.
9. Inspect services and characteristics.
10. Read a readable characteristic.
11. Write to a safe test characteristic if available.
12. Enable notifications on a notify/indicate characteristic.
13. Verify incoming values.
14. Disable notifications.
15. Disconnect.
16. Verify resources are released.
17. Disable Bluetooth during connection and verify graceful handling.
18. Move the device out of range and verify disconnect handling.

Important:

Do not perform destructive or potentially unsafe writes against unknown devices.

For arbitrary nearby devices, prefer read/discovery/notification operations.

---

# 24. ESP32 testing

Do NOT make ESP32 a mandatory dependency of the implementation.

However, structure the application so an ESP32 can later be used as a deterministic BLE peripheral.

If a real GATT read/write/notification test device is needed, an ESP32 can expose a simple custom service such as:

```text
Service
XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX

Characteristics

Read
XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXX1

Write
XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXX2

Notify
XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXX3
```

This is optional for Phase 2 implementation and should not be required to compile or test the application.

---

# 25. Garmin considerations

Do not implement Garmin-specific protocol logic.

The Venu Sq may:

- advertise
- appear during scanning
- expose limited GATT functionality
- expose proprietary services
- reject connections
- require Garmin-specific mechanisms for some functionality

The application should simply inspect what the device actually exposes.

Never assume that a visible device can be freely controlled through generic GATT.

---

# 26. Logging

Add useful debug logging around:

- GATT connection start
- connection state changes
- service discovery
- read start/result
- write start/result
- notification enable/disable
- notification received
- GATT errors
- disconnect

Avoid:

- logging sensitive data unnecessarily
- logging full raw payloads in production
- logging private device information unnecessarily

Use the project's existing logging abstraction if one exists.

---

# 27. UI/UX requirements

Keep the UI clean and technical without becoming cluttered.

Important states must be obvious:

```text
Connecting...
Connected
Disconnected
Error
Reading...
Writing...
Notifications enabled
```

Use Material 3 components.

Handle:

- loading states
- empty services
- empty characteristics
- errors
- disabled actions
- long UUIDs
- long hexadecimal payloads
- small screens
- scrolling

Do not use fake data in the production UI.

---

# 28. What is explicitly OUT OF SCOPE

Do NOT implement:

- Garmin Connect IQ
- Garmin-specific protocol decoding
- Samsung TV control
- ESP32 integration as a required feature
- BLE peripheral/server mode
- advertising from the Android phone
- pairing/bonding UI
- background persistent BLE service
- automatic reconnect strategy
- foreground service
- advanced PHY selection
- MTU negotiation UI
- throughput benchmarking
- firmware updates
- DFU
- CI/CD

Those can be considered future phases.

---

# 29. Code quality

Follow these principles:

- Single responsibility
- Dependency inversion
- Small interfaces
- No Android BLE leakage into domain
- No business logic inside Compose
- No business logic inside Activities/Fragments
- No unnecessary abstraction layers
- No giant ViewModels
- No duplicated BLE logic
- Explicit state management
- Structured concurrency
- Proper cancellation
- Resource cleanup

Prefer readable production-quality code over clever code.

---

# 30. Documentation

Update the README with a dedicated Phase 2 section.

Include:

## BLE GATT

Explain:

- What GATT is
- GAP vs GATT at a high level
- Client/server relationship
- Services
- Characteristics
- Properties
- Read
- Write
- Notify/Indicate
- CCCD
- Connection lifecycle
- GATT operation serialization

Include an architecture diagram similar to:

```text
Compose UI
    ↓
ViewModel
    ↓
UseCase
    ↓
Repository
    ↓
BleGattClient
    ↓
BluetoothGatt
    ↓
BLE Peripheral
```

Also document:

- Phase 1 vs Phase 2
- Supported functionality
- Known limitations
- Garmin limitations
- Future Phase 3 ideas

---

# 31. Final verification

Before finishing:

### Build

- Project compiles.
- All modules compile independently where appropriate.
- No unnecessary warnings introduced.

### Tests

- Existing Phase 1 tests still pass.
- New Phase 2 tests pass.

### Architecture

Verify:

```text
UI
 ↓
ViewModel
 ↓
UseCase
 ↓
Repository
 ↓
BLE abstraction
 ↓
Android BLE API
```

No Android BLE types should leak into domain/UI.

### BLE

Verify:

- Connect
- Disconnect
- Service discovery
- Read
- Write
- Notify/Indicate
- Operation queue
- Error handling
- Lifecycle cleanup

### UI

Verify:

- Loading states
- Empty states
- Error states
- Connected/disconnected states
- Long UUIDs
- Raw byte display
- Notification stream

---

# 32. Final response after implementation

At the end, provide a concise summary containing:

1. What was implemented.
2. Files/modules changed.
3. Architecture changes.
4. GATT functionality supported.
5. Tests added.
6. What was physically verified.
7. Any limitations or issues found.
8. Suggested Phase 3 scope.

Do not claim physical-device verification if it was not actually performed.

The implementation should be production-quality POC code, not a toy example.

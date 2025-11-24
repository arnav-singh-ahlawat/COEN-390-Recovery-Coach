package com.team11.smartgym.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * BLE manager that speaks the same protocol as your NanoHR firmware.
 *
 * - HR via notifications
 * - Temp/Humidity via command -> read TEMP -> read HUM
 * - Steps via IMU command and **polling read** (just like the prototype app)
 */
class BleManager private constructor(
    private val appContext: Context
) {

    private val logTag = "BleManager"

    // Coroutine scope for BLE work
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Bluetooth core objects
    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null
    private var hrCharacteristic: BluetoothGattCharacteristic? = null
    private var tempCharacteristic: BluetoothGattCharacteristic? = null
    private var humCharacteristic: BluetoothGattCharacteristic? = null
    private var controlCharacteristic: BluetoothGattCharacteristic? = null
    private var stepsCharacteristic: BluetoothGattCharacteristic? = null

    // Last control command written (for env flow)
    private var lastControlCommand: Byte? = null

    // Polling job for steps (IMU)
    private var imuPollingJob: Job? = null

    // ---- Public state as StateFlows ----

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Idle)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()

    private val _heartRate = MutableStateFlow<Int?>(null)
    val heartRate: StateFlow<Int?> = _heartRate.asStateFlow()

    private val _temperatureC = MutableStateFlow<Float?>(null)
    val temperatureC: StateFlow<Float?> = _temperatureC.asStateFlow()

    private val _humidity = MutableStateFlow<Float?>(null)
    val humidity: StateFlow<Float?> = _humidity.asStateFlow()

    private val _steps = MutableStateFlow<Long?>(null)
    val steps: StateFlow<Long?> = _steps.asStateFlow()

    companion object {
        @Volatile
        private var instance: BleManager? = null

        fun getInstance(context: Context): BleManager {
            return instance ?: synchronized(this) {
                instance ?: BleManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // ---- Public API ----

    fun startScan() {
        scope.launch {
            if (!ensureBluetoothReady()) return@launch
            if (!hasScanPermission()) {
                Log.w(logTag, "Missing BLUETOOTH_SCAN or location permission")
                _connectionState.value = BleConnectionState.Error("Scan permission not granted")
                return@launch
            }

            _devices.value = emptyList()
            _connectionState.value = BleConnectionState.Scanning

            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BleContracts.SERVICE_UUID))
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            try {
                bleScanner?.startScan(listOf(filter), settings, scanCallback)
            } catch (e: SecurityException) {
                Log.e(logTag, "startScan: SecurityException", e)
                _connectionState.value = BleConnectionState.Error("Scan security exception")
            }
        }
    }

    fun stopScan() {
        scope.launch {
            try {
                bleScanner?.stopScan(scanCallback)
            } catch (e: SecurityException) {
                Log.e(logTag, "stopScan: SecurityException", e)
            } finally {
                if (_connectionState.value is BleConnectionState.Scanning) {
                    _connectionState.value = BleConnectionState.Idle
                }
            }
        }
    }

    fun connectToDevice(address: String, name: String?) {
        scope.launch {
            if (!ensureBluetoothReady()) return@launch
            if (!hasConnectPermission()) {
                Log.w(logTag, "Missing BLUETOOTH_CONNECT permission")
                _connectionState.value = BleConnectionState.Error("Connect permission not granted")
                return@launch
            }

            stopScan()

            val device = bluetoothAdapter?.getRemoteDevice(address)
            if (device == null) {
                _connectionState.value = BleConnectionState.Error("Device not found: $address")
                return@launch
            }

            _connectionState.value = BleConnectionState.Connecting(name, address)

            try {
                bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    @Suppress("DEPRECATION")
                    device.connectGatt(appContext, false, gattCallback)
                }
            } catch (e: SecurityException) {
                Log.e(logTag, "connectGatt: SecurityException", e)
                _connectionState.value = BleConnectionState.Error("Connect security exception")
            }
        }
    }

    fun disconnect() {
        scope.launch {
            imuPollingJob?.cancel()
            imuPollingJob = null

            val gatt = bluetoothGatt ?: return@launch
            _connectionState.value = BleConnectionState.Disconnecting
            try {
                if (hasConnectPermission()) {
                    gatt.disconnect()
                } else {
                    Log.w(logTag, "Missing BLUETOOTH_CONNECT for disconnect; closing GATT directly")
                    gatt.close()
                    bluetoothGatt = null
                    _connectionState.value = BleConnectionState.Idle
                }
            } catch (e: SecurityException) {
                Log.e(logTag, "disconnect: SecurityException", e)
                gatt.close()
                bluetoothGatt = null
                _connectionState.value = BleConnectionState.Idle
            }
        }
    }

    fun requestEnvMeasurement() {
        scope.launch {
            val gatt = bluetoothGatt ?: return@launch
            val control = controlCharacteristic ?: return@launch

            if (!hasConnectPermission()) {
                Log.w(logTag, "Missing BLUETOOTH_CONNECT for env measurement")
                return@launch
            }

            lastControlCommand = BleContracts.CMD_MEASURE_ENV
            control.value = byteArrayOf(BleContracts.CMD_MEASURE_ENV)
            try {
                gatt.writeCharacteristic(control)
            } catch (e: SecurityException) {
                Log.e(logTag, "requestEnvMeasurement: SecurityException", e)
            }
        }
    }

    fun startWorkoutImu() {
        scope.launch {
            val gatt = bluetoothGatt ?: return@launch
            val control = controlCharacteristic ?: return@launch

            if (!hasConnectPermission()) {
                Log.w(logTag, "Missing BLUETOOTH_CONNECT for startWorkoutImu")
                return@launch
            }

            lastControlCommand = BleContracts.CMD_START_IMU
            control.value = byteArrayOf(BleContracts.CMD_START_IMU)
            try {
                gatt.writeCharacteristic(control)
                startImuPolling()
            } catch (e: SecurityException) {
                Log.e(logTag, "startWorkoutImu: SecurityException", e)
            }
        }
    }

    fun stopWorkoutImu() {
        scope.launch {
            imuPollingJob?.cancel()
            imuPollingJob = null

            val gatt = bluetoothGatt ?: return@launch
            val control = controlCharacteristic ?: return@launch

            if (!hasConnectPermission()) {
                Log.w(logTag, "Missing BLUETOOTH_CONNECT for stopWorkoutImu")
                return@launch
            }

            lastControlCommand = BleContracts.CMD_STOP_IMU
            control.value = byteArrayOf(BleContracts.CMD_STOP_IMU)
            try {
                gatt.writeCharacteristic(control)
            } catch (e: SecurityException) {
                Log.e(logTag, "stopWorkoutImu: SecurityException", e)
            }
        }
    }

    // ---- Internal helpers ----

    private fun startImuPolling() {
        imuPollingJob?.cancel()
        imuPollingJob = scope.launch {
            while (true) {
                val gatt = bluetoothGatt ?: break
                val stepsChar = stepsCharacteristic
                if (stepsChar != null && hasConnectPermission()) {
                    try {
                        gatt.readCharacteristic(stepsChar)
                    } catch (e: SecurityException) {
                        Log.e(logTag, "IMU polling read STEPS: SecurityException", e)
                        break
                    }
                }
                delay(1000L)
            }
        }
    }

    private fun ensureBluetoothReady(): Boolean {
        if (bluetoothAdapter == null) {
            _connectionState.value = BleConnectionState.Error("Bluetooth not supported")
            return false
        }
        if (bluetoothAdapter.isEnabled != true) {
            _connectionState.value = BleConnectionState.Error("Bluetooth is off")
            return false
        }
        return true
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // ---- Scan callback ----

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val device = result.device ?: return

            val name = device.name ?: result.scanRecord?.deviceName
            val address = device.address ?: return

            val existing = _devices.value
            if (existing.any { it.address == address }) return

            val newList = existing + BleDevice(name = name, address = address)
            _devices.value = newList

            Log.d(logTag, "Found device: name=$name address=$address")
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(logTag, "Scan failed: code=$errorCode")
            _connectionState.value = BleConnectionState.Error("Scan failed: $errorCode")
        }
    }

    // ---- GATT callback ----

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(logTag, "onConnectionStateChange: status=$status, newState=$newState")
                bluetoothGatt?.close()
                bluetoothGatt = null
                imuPollingJob?.cancel()
                imuPollingJob = null
                _connectionState.value = BleConnectionState.Error("GATT error: $status")
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(logTag, "Connected to GATT server, discovering services...")
                    try {
                        gatt?.discoverServices()
                    } catch (e: SecurityException) {
                        Log.e(logTag, "discoverServices: SecurityException", e)
                        _connectionState.value = BleConnectionState.Error("Discover services security exception")
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(logTag, "Disconnected from GATT server")
                    imuPollingJob?.cancel()
                    imuPollingJob = null
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    hrCharacteristic = null
                    tempCharacteristic = null
                    humCharacteristic = null
                    controlCharacteristic = null
                    stepsCharacteristic = null
                    _connectionState.value = BleConnectionState.Idle
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)

            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                Log.e(logTag, "onServicesDiscovered failed: status=$status")
                _connectionState.value = BleConnectionState.Error("Service discovery failed ($status)")
                return
            }

            val service = gatt.getService(BleContracts.SERVICE_UUID)
            if (service == null) {
                Log.e(logTag, "HR service not found on device")
                _connectionState.value = BleConnectionState.Error("HR service not found")
                return
            }

            hrCharacteristic = service.getCharacteristic(BleContracts.HR_CHAR_UUID)
            tempCharacteristic = service.getCharacteristic(BleContracts.TEMP_CHAR_UUID)
            humCharacteristic = service.getCharacteristic(BleContracts.HUM_CHAR_UUID)
            controlCharacteristic = service.getCharacteristic(BleContracts.CONTROL_CHAR_UUID)
            stepsCharacteristic = service.getCharacteristic(BleContracts.STEPS_CHAR_UUID)

            val name = gatt.device?.name
            val address = gatt.device?.address.orEmpty()
            _connectionState.value = BleConnectionState.Connected(name, address)

            // HR uses notifications; steps are polled like in the prototype WorkoutActivity.
            enableNotifications(gatt, hrCharacteristic)

            Log.d(logTag, "Services discovered, HR notifications enabled")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (characteristic == null) return

            val uuid = characteristic.uuid
            val data = characteristic.value ?: return

            when (uuid) {
                BleContracts.HR_CHAR_UUID -> {
                    if (data.isNotEmpty()) {
                        val bpm = data[0].toInt() and 0xFF
                        _heartRate.value = bpm
                    }
                }

                // We don't rely on notifications for steps, but this stays just in case.
                BleContracts.STEPS_CHAR_UUID -> {
                    if (data.size >= 4) {
                        val steps =
                            (data[0].toLong() and 0xFF) or
                                    ((data[1].toLong() and 0xFF) shl 8) or
                                    ((data[2].toLong() and 0xFF) shl 16) or
                                    ((data[3].toLong() and 0xFF) shl 24)
                        _steps.value = steps
                    }
                }

                BleContracts.TEMP_CHAR_UUID -> {
                    if (data.size >= 2) {
                        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        val raw = buffer.short.toInt() // SINT16 °C * 100
                        _temperatureC.value = raw / 100.0f
                    }
                }

                BleContracts.HUM_CHAR_UUID -> {
                    if (data.size >= 2) {
                        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                        val raw = buffer.short.toInt() // SINT16 %RH * 100
                        _humidity.value = raw / 100.0f
                    }
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (status != BluetoothGatt.GATT_SUCCESS || characteristic == null) return

            // Reuse notification handler to parse bytes for TEMP / HUM / STEPS
            onCharacteristicChanged(gatt, characteristic)

            // After reading TEMP as part of CMD_MEASURE_ENV, chain a HUM read
            if (characteristic.uuid == BleContracts.TEMP_CHAR_UUID) {
                val humChar = humCharacteristic
                if (gatt != null && humChar != null && hasConnectPermission()) {
                    try {
                        gatt.readCharacteristic(humChar)
                    } catch (e: SecurityException) {
                        Log.e(logTag, "onCharacteristicRead(TEMP) -> read HUM: SecurityException", e)
                    }
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null || characteristic == null) return

            if (characteristic.uuid == BleContracts.CONTROL_CHAR_UUID) {
                when (lastControlCommand) {
                    BleContracts.CMD_MEASURE_ENV -> {
                        val tempChar = tempCharacteristic
                        if (tempChar != null && hasConnectPermission()) {
                            try {
                                gatt.readCharacteristic(tempChar)
                            } catch (e: SecurityException) {
                                Log.e(logTag, "onCharacteristicWrite -> read TEMP: SecurityException", e)
                            }
                        }
                    }

                    BleContracts.CMD_START_IMU -> {
                        // First read should get 0; polling loop will keep updating.
                        val stepsChar = stepsCharacteristic
                        if (stepsChar != null && hasConnectPermission()) {
                            try {
                                gatt.readCharacteristic(stepsChar)
                            } catch (e: SecurityException) {
                                Log.e(logTag, "onCharacteristicWrite(CMD_START_IMU) -> read STEPS: SecurityException", e)
                            }
                        }
                    }

                    BleContracts.CMD_STOP_IMU -> {
                        Log.d(logTag, "CMD_STOP_IMU acknowledged")
                    }
                }
            }
        }
    }

    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic?
    ) {
        if (characteristic == null) return
        if (!hasConnectPermission()) {
            Log.w(logTag, "Missing BLUETOOTH_CONNECT for enableNotifications")
            return
        }

        try {
            gatt.setCharacteristicNotification(characteristic, true)
            val cccd = characteristic.getDescriptor(BleContracts.CCCD_UUID)
            if (cccd != null) {
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(cccd)
            } else {
                Log.w(logTag, "CCCD not found for characteristic ${characteristic.uuid}")
            }
        } catch (e: SecurityException) {
            Log.e(logTag, "enableNotifications: SecurityException", e)
        }
    }
}

package com.team11.smartgym.ble

import java.util.UUID

object BleContracts {

    // Must match Arduino: BLEService hrService("12345678-1234-5678-1234-56789abcdef0");
    val SERVICE_UUID: UUID =
        UUID.fromString("12345678-1234-5678-1234-56789abcdef0")

    // Heart rate characteristic (BLEUnsignedCharCharacteristic)
    val HR_CHAR_UUID: UUID =
        UUID.fromString("12345678-1234-5678-1234-56789abcdef1")

    // Temperature SINT16 (°C * 100)
    val TEMP_CHAR_UUID: UUID =
        UUID.fromString("12345678-1234-5678-1234-56789abcdef2")

    // Humidity SINT16 (%RH * 100)
    val HUM_CHAR_UUID: UUID =
        UUID.fromString("12345678-1234-5678-1234-56789abcdef3")

    // Control: command byte
    val CONTROL_CHAR_UUID: UUID =
        UUID.fromString("12345678-1234-5678-1234-56789abcdef4")

    // Steps: UINT32
    val STEPS_CHAR_UUID: UUID =
        UUID.fromString("12345678-1234-5678-1234-56789abcdef5")

    // Standard Client Characteristic Configuration descriptor (CCCD) UUID
    val CCCD_UUID: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Commands (must match the Arduino CMD_* constants)
    const val CMD_MEASURE_ENV: Byte = 0x01
    const val CMD_START_IMU: Byte = 0x10
    const val CMD_STOP_IMU: Byte = 0x11
}

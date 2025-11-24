package com.team11.smartgym.ble

sealed interface BleConnectionState {
    data object Idle : BleConnectionState
    data object Scanning : BleConnectionState
    data class Connecting(val deviceName: String?, val address: String) : BleConnectionState
    data class Connected(val deviceName: String?, val address: String) : BleConnectionState
    data object Disconnecting : BleConnectionState
    data class Error(val message: String) : BleConnectionState
}

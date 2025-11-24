@file:OptIn(ExperimentalPermissionsApi::class)

package com.team11.smartgym.ui.permissions

import android.Manifest
import android.os.Build
import androidx.compose.runtime.Composable
import com.google.accompanist.permissions.*

@Composable
fun rememberBlePermissionsState(): MultiplePermissionsState {
    val permissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    return rememberMultiplePermissionsState(permissions)
}

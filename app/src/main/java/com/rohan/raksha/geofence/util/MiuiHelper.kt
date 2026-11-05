package com.rohan.raksha.geofence.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

object MiuiHelper {
    fun isMiui(): Boolean {
        return Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
                Build.MANUFACTURER.equals("POCO", ignoreCase = true) ||
                Build.MANUFACTURER.equals("Redmi", ignoreCase = true)
    }

    fun getAutostartIntent(): Intent {
        return Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        }
    }
}

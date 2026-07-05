package com.ekam.baton.feature.settings

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

object DeviceInfoHelper {
    fun getDiagnosticInfo(context: Context): String {
        val appVersion = try {
            val pInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "Unknown"
        }

        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        val osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        return """
            
            -------------------------
            Device Diagnostics:
            App Version: $appVersion
            Device Model: $deviceModel
            OS Version: $osVersion
            -------------------------
        """.trimIndent()
    }
}

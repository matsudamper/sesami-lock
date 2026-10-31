package net.matsudamper.sesamilock

import android.content.Context
import android.content.pm.PackageManager
import net.matsudamper.sesamilock.sesame.ble.SesameBleTransport

internal class BlePermissions(private val context: Context) {
    val required: List<String> = SesameBleTransport.requiredRuntimePermissions()

    fun isGranted(): Boolean = required.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
}

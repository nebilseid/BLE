package com.example.bluetoothtest

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import java.nio.charset.Charset
import java.util.*

object SimpleTextProfile {
    //E20A39F4-73F5-4BC4-A12F-17D1AD07A961
    val SIMPLE_TEXT_SERVICE = UUID.fromString("EE20A39F4-73F5-4BC4-A12F-17D1AD07A961")
    val SIMPLE_TEXT = UUID.fromString("08590F7E-DB05-467E-8757-72F6FAEB13D4")
    val CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var currentColor = "#FF00FF"

    fun createSimpleTextService(): BluetoothGattService {
        val service = BluetoothGattService(SIMPLE_TEXT_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val simpleText = BluetoothGattCharacteristic(SIMPLE_TEXT,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
        BluetoothGattCharacteristic.PERMISSION_READ)

        val configDescriptor = BluetoothGattDescriptor(CLIENT_CONFIG,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
        simpleText.addDescriptor(configDescriptor)

        service.addCharacteristic(simpleText)
        return service
    }

    fun setCurrentColor(color: String) {
        currentColor = color
    }

    fun getColor() = currentColor.toByteArray(Charset.defaultCharset())
}
package com.example.bluetoothtest

import android.app.Application
import com.polidea.rxandroidble2.RxBleClient

class MyApp : Application() {

    companion object {
        lateinit var rxBleClient: RxBleClient
            private set
    }

    override fun onCreate() {
        super.onCreate()
        rxBleClient = RxBleClient.create(this)
    }


}
package com.example.bluetoothtest

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.scan.ScanFilter
import com.polidea.rxandroidble2.scan.ScanResult
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_main.*
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val RC_LOCATION_PERMISSION = 1010
        private const val TAG = "MainActivity"
    }

    enum class SimpleTextPayload(val data: String) {
        GREEN("#228B22"),
        BLUE("#0000FF")
    }

    private val requiredPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    private val compositeDisposable = CompositeDisposable()

    private lateinit var bluetoothManager: BluetoothManager
    private var bluetoothGattServer: BluetoothGattServer? = null

    private val registeredDevices = mutableSetOf<BluetoothDevice>()

    private val connectionPool = mutableMapOf<String, Disposable>()

    private val logAdapter = LogAdapter()

    //Used for broadcast receiver
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)
            when (state) {
                BluetoothAdapter.STATE_ON -> {
                    Log.d(TAG, "Starting")
                    addToLog("Starting from receiver")
                    startAdvertising()
                    startServer()
                }
                BluetoothAdapter.STATE_OFF -> {
                    stopServer()
                    stopAdvertising()
                    addToLog("Stopping from receiver")
                }
            }
        }
    }

    //Callback for advertising BLE services
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "advertising successfully")
            addToLog("advertising successfully")
            startServer()
        }

        override fun onStartFailure(errorCode: Int) {
            Log.d(TAG, "advertising failed")
            addToLog("advertising failed $errorCode")
        }
    }

    //BLE server callbacks
    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "BluetoothDevice CONNECTED: ${device.address}")
                //registeredDevices.add(device)
                addToLog("BluetoothDevice CONNECTED: ${device.address}")

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "BluetoothDevice DISCONNECTED: $device")
                registeredDevices.remove(device)
                //Free up the actual connection, other places could be considered as well?
                connectionPool[device.address]?.dispose()
                addToLog("BluetoothDevice Disconnected: ${device.address}")
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            registeredDevices.add(device)
            when (SimpleTextProfile.SIMPLE_TEXT) {
                characteristic.uuid -> {
                    Log.i(TAG, "Read Simple Text")
                    addToLog("Charecteristic Read simple text")
                    bluetoothGattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        SimpleTextProfile.getColor()
                    )
                }
                //Support additional characteristics
                else -> {
                    // Invalid characteristic
                    Log.w(TAG, "Invalid Characteristic Read: " + characteristic.uuid)
                    addToLog("Invalid Characteristic Read: $characteristic.uuid")

                    bluetoothGattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null
                    )
                }
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice, requestId: Int, offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            if (SimpleTextProfile.CLIENT_CONFIG == descriptor.uuid) {
                Log.d(TAG, "Config descriptor read")
                addToLog("Descriptor Read request: SimpleTextProfile " + descriptor.uuid)
                val returnValue = if (registeredDevices.contains(device)) {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE.also {
                        addToLog("Notification value enabled")
                    }

                } else {
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE.also {
                        addToLog("Notification value disabled")
                    }
                }
                bluetoothGattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    returnValue
                )
            } else {
                Log.w(TAG, "Unknown descriptor read request")
                addToLog("Unknown descriptor read request")
                bluetoothGattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_FAILURE,
                    0, null
                )
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray
        ) {
            if (SimpleTextProfile.CLIENT_CONFIG == descriptor.uuid) {
                if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(TAG, "Subscribe device to notifications: $device")
                    registeredDevices.add(device)
                    addToLog("Descriptor Write: Subscribe device to notifications ${device.address}")
                } else if (Arrays.equals(
                        BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE,
                        value
                    )
                ) {
                    Log.d(TAG, "Unsubscribe device from notifications: $device")
                    registeredDevices.remove(device)
                    addToLog("Descriptor Write: Unsubscribe device to notifications ${device.address}")
                }

                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0, null
                    )
                }
            } else {
                Log.w(TAG, "Unknown descriptor write request")
                addToLog("Descriptor Write: Unknown descriptor ${device.address}")
                if (responseNeeded) {
                    bluetoothGattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0, null
                    )
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

       // rv_log.addItemDecoration(DividerItemDecoration(this, layoutManager.orientation))
        rv_log.adapter = logAdapter
        btn_scan.setOnClickListener {
            checkPermissions()
        }

        btn_green.setOnClickListener {
            onColorSelected(SimpleTextPayload.GREEN)
        }

        btn_blue.setOnClickListener {
            onColorSelected(SimpleTextPayload.BLUE)
        }

        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothReceiver, filter)

        if (!bluetoothAdapter.isEnabled) {
            // bluetoothAdapter.enable() shouldn't do this
        } else {
            startAdvertising()
            //  startServer()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    @AfterPermissionGranted(RC_LOCATION_PERMISSION)
    private fun checkPermissions() {
        if (EasyPermissions.hasPermissions(this, *requiredPermissions)) {
            startScanning()
        } else {
            EasyPermissions.requestPermissions(
                this,
                "Please grant permissions",
                RC_LOCATION_PERMISSION,
                *requiredPermissions
            )
        }
    }

    private fun startScanning() {
        addToLog("Starting scan")
        if (MyApp.rxBleClient.isScanRuntimePermissionGranted) {
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()
            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SimpleTextProfile.SIMPLE_TEXT_SERVICE))
                .build()

            MyApp.rxBleClient.scanBleDevices(scanSettings, scanFilter)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ connectToDevice(it) }, { it.printStackTrace() })
                .let { compositeDisposable.add(it) }
        }
    }

    private fun connectToDevice(scanResult: ScanResult) {
        //Quick check to prevent additional connections, use boolean
        if (registeredDevices.firstOrNull { it.address == scanResult.bleDevice.bluetoothDevice.address } == null) {
            val device = MyApp.rxBleClient.getBleDevice(scanResult.bleDevice.macAddress)
            /* if (device.connectionState == RxBleConnection.RxBleConnectionState.CONNECTED) {
                 registeredDevices.add(device.bluetoothDevice)
                 addToLog("Added already connected device ${device.macAddress}")
                 return
             }*/
            if (device.connectionState != RxBleConnection.RxBleConnectionState.CONNECTING && device.connectionState != RxBleConnection.RxBleConnectionState.CONNECTED) {
                addToLog("Attempting to connect to Device: ${scanResult.bleDevice.bluetoothDevice.address}")
                device.establishConnection(false)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ onDeviceConnected(it) }, {
                        it.printStackTrace()
                        addToLog("Error: connecting to Device: ${it.message}")

                    })
                    .let {
                        compositeDisposable.add(it)
                        //keep track of disposables individually, these are used to actually disconnect the BLE connections
                        connectionPool[device.macAddress] = it
                    }
            }
        }
    }

    private fun onDeviceConnected(rxBleConnection: RxBleConnection) {
//        rxBleConnection.readCharacteristic(SimpleTextProfile.SIMPLE_TEXT)
//            .observeOn(AndroidSchedulers.mainThread())
//            .subscribe( {onSimpleTextRead(it) }, { it.printStackTrace() })
//            .let { compositeDisposable.add(it) }
        //setup notification
        addToLog("Device connected")
        rxBleConnection.setupNotification(SimpleTextProfile.SIMPLE_TEXT)
            .observeOn(AndroidSchedulers.mainThread())
            .flatMap { it }
            .subscribe({ onSimpleTextRead(it) }, { it.printStackTrace() })
            .let { compositeDisposable.add(it) }
    }

    private fun onSimpleTextRead(result: ByteArray) {
        runOnUiThread {
            main_activity_container.setBackgroundColor(Color.parseColor(String(result)))
        }
    }

    private fun onColorSelected(payload: SimpleTextPayload) {
        if (registeredDevices.isEmpty()) {
            Log.i(TAG, "No subscribers registered")
            addToLog("No subscribers registered")
            return
        }
        SimpleTextProfile.setCurrentColor(payload.data)

        Log.i(TAG, "Sending update to ${registeredDevices.size} subscribers")
        addToLog("Sending update to ${registeredDevices.size} subscribers")

        for (device in registeredDevices) {
            val simpleTextCharacteristic = bluetoothGattServer
                ?.getService(SimpleTextProfile.SIMPLE_TEXT_SERVICE)
                ?.getCharacteristic(SimpleTextProfile.SIMPLE_TEXT)
            simpleTextCharacteristic?.value = SimpleTextProfile.getColor()
            bluetoothGattServer?.notifyCharacteristicChanged(
                device,
                simpleTextCharacteristic,
                false
            )
        }
    }

    private fun startServer() {
        bluetoothGattServer = bluetoothManager.openGattServer(this, gattServerCallback)

        bluetoothGattServer?.addService(SimpleTextProfile.createSimpleTextService()).also {
            addToLog("Started Server from startserver()")
        }
            ?: Log.w("MainActivity", "Unable to create GATT server").also {
                addToLog("Unable to create GATT server")
            }

    }

    private fun startAdvertising() {
        val bluetoothLeAdvertiser: BluetoothLeAdvertiser? =
            bluetoothManager.adapter.bluetoothLeAdvertiser

        bluetoothLeAdvertiser?.let {
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(ParcelUuid(SimpleTextProfile.SIMPLE_TEXT_SERVICE))
                .build()

            it.startAdvertising(settings, data, advertiseCallback)
            addToLog("Started advertising")
        } ?: Log.w(TAG, "Failed to create advertiser").also {
            addToLog("Unable to create advertiser")

        }
    }

    private fun stopServer() {
        bluetoothGattServer?.close()
        addToLog("stopped server from stopServer()")
    }

    private fun stopAdvertising() {
        val bluetoothLeAdvertiser: BluetoothLeAdvertiser? =
            bluetoothManager.adapter.bluetoothLeAdvertiser
        bluetoothLeAdvertiser?.let {
            it.stopAdvertising(advertiseCallback).also {
                addToLog("Stopped advertiser")
            }
        } ?: Log.w(TAG, "Failed to create advertiser").also {
            addToLog("Unable to stop advertising")
        }
    }

    private fun addToLog(message: String) {
        //Most callbacks are asynchronous, there's a good chance a background thread is calling this
        //switch to UI thread
        runOnUiThread {
            logAdapter.addItem(message)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.clear()
        if (bluetoothManager.adapter.isEnabled) {
            stopServer()
            stopAdvertising()
        }
        unregisterReceiver(bluetoothReceiver)
        addToLog("onDestroy")
    }
}

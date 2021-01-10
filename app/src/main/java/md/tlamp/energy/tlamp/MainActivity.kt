package md.tlamp.energy.tlamp

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff.Mode
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AppCompatActivity
import com.madrapps.pikolo.HSLColorPicker
import com.madrapps.pikolo.listeners.SimpleColorSelectionListener
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {

    private lateinit var colorPicker: HSLColorPicker
    private lateinit var centerImageView: ImageView
    private lateinit var rgbImageView: ImageView
    private lateinit var whiteSeekBar: SeekBar
    private lateinit var whiteImageView: ImageView
    private lateinit var deviceLabel: TextView

    private var white = 0
    private var red   = 0
    private var green = 0
    private var blue  = 0

    private var finalDataToSend: String = ""

    private var mContext: Context? = null
    private lateinit var mBluetoothAdapter: BluetoothAdapter
    private lateinit var outputStream: OutputStream
    private var connectedDevice: BluetoothDevice? = null;


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mContext = this

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        colorPicker = findViewById(R.id.colorPicker)
        centerImageView = findViewById(R.id.centerImageView)
        rgbImageView = findViewById(R.id.rgbImageView)
        whiteImageView = findViewById(R.id.whiteImageView)
        whiteSeekBar = findViewById(R.id.whiteSeekBar)
        deviceLabel = findViewById(R.id.device)

        colorPicker.setColorSelectionListener(object : SimpleColorSelectionListener() {
            override fun onColorSelected(color: Int) {
                setRGBColor(color)
            }
            override fun onColorSelectionEnd(color: Int) {
                sendData()
            }
        })

        whiteSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                sendData()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                setWhiteIntensity(seekBar, progress)
            }
        })
    }

    private fun setRGBColor(color: Int) {
        centerImageView.background.setColorFilter(color, Mode.MULTIPLY)
        rgbImageView.background.setColorFilter(color, Mode.MULTIPLY)

        red = Color.red(color)
        green = Color.green(color)
        blue = Color.blue(color)
    }

    private fun setWhiteIntensity(seekBar: SeekBar, progress: Int) {
        val percents = progress*100/seekBar.max
        val intensity = percents*255/100
        whiteImageView.background.setColorFilter(Color.argb(255-intensity, 255, 255, 255), Mode.MULTIPLY)

        white = intensity
    }

    private fun sendData() {
        finalDataToSend = "W;$white;RGB;$red;$green;$blue"
        Log.d("sent", finalDataToSend)

        GlobalScope.launch {
            try {
                val bytes: ByteArray = finalDataToSend.toByteArray()
                outputStream.write(bytes)
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(mContext, "Please connect to device first.", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.action_name) {

            val pairedDevices: Set<BluetoothDevice> = mBluetoothAdapter.bondedDevices
            val devices: ArrayList<String> = ArrayList()
            for (bt in pairedDevices) {
                val connectedLabel = if (connectedDevice != null && connectedDevice?.address == bt.address) " (connected)" else ""
                devices.add(bt.name + connectedLabel + "\n" + bt.address)
            }
            val arrayAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, devices)

            val builderSingle: AlertDialog.Builder = AlertDialog.Builder(this)
            builderSingle.setTitle("Select from paired devices")
            builderSingle.setNegativeButton("cancel") { dialog, _ -> dialog.dismiss() }

            builderSingle.setAdapter(arrayAdapter) { _, which ->
                val alreadyConnected = connectedDevice != null && pairedDevices.elementAt(which).address == connectedDevice?.address
                if(!alreadyConnected) {
                    connectedDevice = pairedDevices.elementAt(which)
                    deviceLabel.text = "connecting..."
                    GlobalScope.launch {
                        connectedDevice?.let {
                            val mBTSocket = createBluetoothSocket(it)
                            try {
                                mBTSocket!!.connect()
                                runOnUiThread { deviceLabel.text = "connected: ${it.name}" }
                            } catch (e: IOException) {
                                connectedDevice = null
                                runOnUiThread {
                                    deviceLabel.text = ""
                                    Toast.makeText(mContext, "Cannot connect. Please reopen app and try again.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            outputStream = mBTSocket!!.outputStream
                        }
                    }
                }
                else {
                    Toast.makeText(this, "Already connected.", Toast.LENGTH_SHORT).show()
                }
            }
            builderSingle.show()

            return true
        } else super.onOptionsItemSelected(item)
    }


    private val BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @Throws(IOException::class)
    private fun createBluetoothSocket(device: BluetoothDevice): BluetoothSocket? {
        try {
            val m = device.javaClass.getMethod("createInsecureRfcommSocketToServiceRecord", UUID::class.java)
            return m.invoke(device, BTMODULEUUID) as BluetoothSocket
        } catch (e: Exception) {}
        return device.createRfcommSocketToServiceRecord(BTMODULEUUID)
    }
}

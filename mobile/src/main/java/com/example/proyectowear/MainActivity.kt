package com.example.proyectowear

import android.content.Context
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.text.DecimalFormat
import java.time.Instant


val TAG = MainActivity::class.simpleName
class MainActivity : AppCompatActivity(), DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener, SensorEventListener {
    private var activityContext: Context? = null
    private var temperatura: Double = 0.0; //Variable que almacena el valor del sensor de temperatura ambiente
    private var humedad: Double = 0.0; //Variable que almacena el valor del sensor de la luz
    private var presion: Double = 0.0; //Variable que almacena el valor del sensor de presión
    private var acX: Double = 0.0; //Variable que almacena el valor X del sensor del acelerómetro
    private var acY: Double = 0.0; //Variable que almacena el valor de Y del sensor del acelerómetro
    private var acZ: Double = 0.0; //Variable que almacena el valor de Z del sensor del acelerómetro

    companion object {
        const val pathInicio = "/inicio" //Ruta de inicio
        const val pathImagen = "/assetImagen" //Ruta en donde se envía la imagen
        const val imagen_key = "imagen" //Campo en donde se envía la imagen en formato Asset
        const val sensor_key = "/sensores" //Ruta en donde se envía la información de los sensores
        const val temp_key = "temp" //Campo en donde se envía el valor del sensor de temperatura ambiente
        const val hum_key = "humedad" //Campo en donde se envía el valor del sensor de luz
        const val pres_key = "presion" //Campo en donde se envía el valor del sensor de presión
        const val acX_key = "acX" //Campo en donde se envía el valor X del sensor del acelerómetro
        const val acY_key = "acY" //Campo en donde se envía el valor Y del sensor del acelerómetro
        const val acZ_key = "acZ" //Campo en donde se envía el valor Z del sensor del acelerómetro
        const val time_key = "time" //Campo en donde se envía la fecha actual en la que se envían todos los valores
    }

    lateinit var imagenBitmap : Bitmap //Variable en donde se almacena la imagen en formato Bitmap
    lateinit var camara : ImageView //Variable mediante la cual se enlaza con el recuso de la interfaz XML para visualizar la imagen en la app del teléfono

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        camara = findViewById<ImageView>(R.id.imagenCamara) //Se enlaza el recurso de la interfaz XML con la variable camara


        findViewById<Button>(R.id.tomarFoto).apply { //Invoca el método 'intentCamara' que permite tomar una foto al presionar el botón
            setOnClickListener {
                intentCamara()
            }
        }

        activityContext = this
        sensores() //Se invoca el método 'sensores' que permite comenzar el monitoreo de los sensores del teléfono

    }

    //Este método permite monitorear y escuchar los sensores del teléfono
    private fun sensores() {
        val manager = getSystemService(SENSOR_SERVICE) as SensorManager
        // Descomenta las líneas si tu dispositivo tiene estos sensores, de lo contrario la app crasheará
        // val ambTemp: Sensor = manager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        //val press: Sensor = manager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        //manager.registerListener(this, ambTemp, SensorManager.SENSOR_DELAY_NORMAL)
        //manager.registerListener(this, press, SensorManager.SENSOR_DELAY_NORMAL)
        val relHum: Sensor = manager.getDefaultSensor(Sensor.TYPE_LIGHT)
        val acelerometro: Sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        manager.registerListener(this, relHum, SensorManager.SENSOR_DELAY_NORMAL)
        manager.registerListener(this, acelerometro, SensorManager.SENSOR_DELAY_NORMAL)
    }

    //Este método permite detectar cuando la información de un sensor ha cambiado
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_AMBIENT_TEMPERATURE) {
            val msg = "Temperatura: " + event.values[0].toDouble() + "°C"
            Log.d(TAG, msg)
            temperatura = event.values[0].toDouble()
        } else if (event.sensor.type == Sensor.TYPE_LIGHT) {
            val msg = "Humedad Relativa: " + event.values[0].toDouble() + "%"
            Log.d(TAG, msg)
            humedad = event.values[0].toDouble()
        }else if (event.sensor.type == Sensor.TYPE_PRESSURE) {
            val msg = "Presión: " + event.values[0].toDouble() + " hPa"
            Log.d(TAG, msg)
            presion = event.values[0].toDouble()
        }
        else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val msg = "Acelerómetro: X:" + event.values[0].toDouble() + ", Y:" + event.values[1].toDouble() + ", Z: "+event.values[2].toDouble()
            Log.d(TAG, msg)
            acX = event.values[0].toDouble()
            acY = event.values[1].toDouble()
            acZ = event.values[2].toDouble()
        }
        enviarInfoSensores() //Se invoca el método 'enviarInfoSensores' que permite enviar la información a la aplicación del reloj
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "onAccuracyChanged - Precisión: $accuracy")
    }

    //Este método permite hacer un "intento" de tomar utilizar la cámara del teléfono y tomar una foto
    private fun intentCamara() {
        launcher.launch(null)
    }


    //Este método abre la interfaz que permite tomar una foto con la cámara del teléfono, y si la foto es aceptada por el usuario se coloca para que se visualizada en la interfaz de la aplicación móvil y se envía a la aplicación del reloj para que sea visualizada de la misma forma.
    private val launcher = registerForActivityResult(ActivityResultContracts.TakePicturePreview())
    { bitmap ->
        camara.setBackgroundResource(0)
        imagenBitmap = bitmap
        camara.setImageBitmap(bitmap) //Se establece la imagen para que sea visualizada en la aplicación del teléfono
        enviarFoto() //Se invoca el método 'enviarFoto' que permite enviar la foto como un Asset a la aplicación del reloj
    }



    // Este método permite que cuando la aplicación ya ha sido iniciada correctamente se agreguen los eventos de escucha de la API DataClient para poder recibir mensajes e información.
    override fun onResume() {
        Wearable.getDataClient(activityContext!!).addListener(this)
        Wearable.getMessageClient(activityContext!!).addListener(this)
        super.onResume()
    }


    //Este método permite detectar cuando se recibe información por parte de la App del Reloj. No se ha implementado código dentro de ella debido a que el reloj no envía información al móvil.
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        //
    }


    //Este método permite crear un Asset a partir de una imagen en formato Bitmap, para que de esta forma pueda ser enviado mediante la API DataClient.
    private fun createAssetFromBitmap(bitmap: Bitmap?): Asset =
        ByteArrayOutputStream().let { byteStream ->
            bitmap?.compress(Bitmap.CompressFormat.PNG, 100, byteStream)
            Asset.createFromBytes(byteStream.toByteArray())
        }


    //Este método  permite enviar la foto como un Asset a la aplicación del reloj
    private fun enviarFoto() {
        GlobalScope.launch {
            val request: PutDataRequest = PutDataMapRequest.create(pathImagen).apply {
                dataMap.putAsset(imagen_key, createAssetFromBitmap(imagenBitmap))
            }.asPutDataRequest().setUrgent()
            val result = Wearable.getDataClient(activityContext).putDataItem(request).await()
            Log.d(TAG, "DataItem Foto: $result")
        }
    }

    //Este método permite enviar la información de los sensores a la aplicación del reloj
    private fun enviarInfoSensores() {
        val df = DecimalFormat("#.##")
        GlobalScope.launch {
            val request: PutDataRequest = PutDataMapRequest.create(sensor_key).apply {
                dataMap.putDouble(temp_key,df.format(temperatura).toDouble())
                dataMap.putDouble(hum_key,df.format(humedad).toDouble())
                dataMap.putDouble(pres_key,df.format(presion).toDouble())
                dataMap.putDouble(acX_key,df.format(acX).toDouble())
                dataMap.putDouble(acY_key,df.format(acY).toDouble())
                dataMap.putDouble(acZ_key,df.format(acZ).toDouble())
                dataMap.putLong(time_key, Instant.now().epochSecond)
            }.asPutDataRequest().setUrgent()
            val result = Wearable.getDataClient(activityContext).putDataItem(request).await()
            Log.d(TAG, "DataItem Sensores: $result")
        }
    }

    //Este método permite recibir mensajes enviados por el reloj
    override fun onMessageReceived(p0: MessageEvent) {
        try {
            val messageEventPath: String = p0.path
            if (messageEventPath == pathInicio) {
                Log.d(TAG, "Aplicación abierta en el reloj")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
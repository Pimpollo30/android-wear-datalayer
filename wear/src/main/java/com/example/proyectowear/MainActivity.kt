package com.example.proyectowear

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.util.Log
import com.example.proyectowear.databinding.ActivityMainBinding
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GooglePlayServicesUtil
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.data.FreezableUtils
import com.google.android.gms.wearable.*
import java.io.InputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.GlobalScope.coroutineContext
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.coroutineContext

val TAG = MainActivity::class.simpleName

class MainActivity : Activity(), DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener{

    private lateinit var binding: ActivityMainBinding
    private var activityContext: Context? = null

    var imagenBitmap : Bitmap? = null; //Variable en donde se almacena la imagen en formato Bitmap

    companion object {
        const val pathInicio = "/inicio" //Ruta de inicio
        const val pathImagen = "/assetImagen" //Ruta en donde se envía la imagen
        const val imagen_key = "imagen" //Campo en donde se envía la imagen en formato Asset
        const val sensor_key = "/sensores" //Ruta en donde se envía la información de los sensores
        const val temp_key = "temp" //Campo en donde se envía el valor del sensor de temperatura ambiente
        const val hum_key = "humedad" //Campo en donde se envía el valor del sensor de luz
        const val pres_key = "presion" //Campo en donde se envía el valor del sensor de presión
        const val acX_key = "acX"  //Campo en donde se envía el valor X del sensor del acelerómetro
        const val acY_key = "acY"  //Campo en donde se envía el valor Y del sensor del acelerómetro
        const val acZ_key = "acZ"  //Campo en donde se envía el valor Z del sensor del acelerómetro
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        activityContext = this;
    }

    // Este método permite que cuando la aplicación ya ha sido iniciada correctamente se agreguen los eventos de escucha de la API DataClient para poder recibir mensajes e información.
    override fun onResume() {
        Wearable.getDataClient(activityContext!!).addListener(this)
        Wearable.getMessageClient(activityContext!!).addListener(this)
        super.onResume()
    }

     //Este método permite detectar cuando se recibe información por parte de la App del Teléfono, ya sea la imagen capturada en formato Asset o la información de los sensores.
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged(): $dataEvents")
        val events: List<DataEvent> = FreezableUtils.freezeIterable(dataEvents)
        dataEvents.close()
        for (event in events) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                if (pathImagen == path) { //Si la ruta de donde se origina el cambio es igual a la ruta de donde se envía la imagen, se procede a obtener la imagen para posteriormente visualizarla en la App del Reloj.
                    val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                    val photo = dataMapItem.dataMap.getAsset(imagen_key)

                    runBlocking {
                        val job = GlobalScope.launch {
                            imagenBitmap = loadBitmapFromAsset(photo)
                        }
                        job.join()
                    }
                    binding.imagen.setBackgroundResource(0)
                    binding.imagen.setImageBitmap(imagenBitmap)
                    Log.i(TAG, "Aplicando imagen bitmap")

                }  else if (sensor_key == path) { //Si la ruta de donde se origina el cambio es igual a la ruta de donde se envía la información de los sensores, se procede a obtener estos valores para posteriormente visualizarlos en la App del Reloj.
                    val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                    val temperatura = dataMapItem.dataMap.getDouble(temp_key)
                    val humedad = dataMapItem.dataMap.getDouble(hum_key)
                    val presion = dataMapItem.dataMap.getDouble(pres_key)
                    val acX = dataMapItem.dataMap.getDouble(acX_key)
                    val acY = dataMapItem.dataMap.getDouble(acY_key)
                    val acZ = dataMapItem.dataMap.getDouble(acZ_key)
                    binding.temperatura.text = "Temperatura: $temperatura°C"
                    binding.humedad.text = "Luz: $humedad lx"
                    binding.presion.text = "Presión: $presion hPa"
                    binding.giroscopio.text = "Acelerómetro:\nX: $acX\nY: $acY\nZ: $acZ"
                    if (temperatura < 35) { //Si la temperatura es menor a 35°C, el color del shape (círculo) será de color verde
                        binding.circulo.setColorFilter(Color.rgb(0,255,0), PorterDuff.Mode.SRC_ATOP)
                    } else if (temperatura >= 35 && temperatura <= 45) {  //Si la temperatura es mayor o igual a 35°C y menor o igual a 45°C, el color del shape (círculo) será de color amarillo
                        binding.circulo.setColorFilter(Color.rgb(255,255,0), PorterDuff.Mode.SRC_ATOP)
                    } else if (temperatura > 45) { //Si la temperatura es mayor 45°C, el color del shape (círculo) será de color rojo
                        binding.circulo.setColorFilter(Color.rgb(255,0,0), PorterDuff.Mode.SRC_ATOP)
                    }
                    binding.barHum.setProgress(humedad.toInt(),false)
                    val colorFilter = PorterDuffColorFilter(Color.rgb(139,0,255), PorterDuff.Mode.SRC_ATOP)
                    binding.barHum.progressDrawable.colorFilter = colorFilter

                } else {
                    Log.d(TAG, "No se reconoce el path: $path")
                }
            } else if (event.type == DataEvent.TYPE_DELETED) {
                Log.d(TAG,"DataItem eliminado" + event.dataItem.toString())
            } else {
                Log.d(TAG,"No se reconoce el tipo de evento: " + event.type)
            }

        }
    }

    //Este método permite crear una imagen Bitmap a partir de la imagen que fue enviada en formato Asset, para que de esta forma pueda ser visualizada en la App del Reloj.
    private suspend fun loadBitmapFromAsset(asset: Asset?): Bitmap? {
        requireNotNull(asset) { "El asset no debe ser nulo" }
        val assetInputStream: InputStream? = Wearable.getDataClient(activityContext)
            .getFdForAsset(asset).await().inputStream
        if (assetInputStream == null) {
            Log.w(TAG, "Asset desconocido")
            return null
        }
        return BitmapFactory.decodeStream(assetInputStream)
    }

    //Este método permite recibir mensajes enviados por el reloj
    override fun onMessageReceived(p0: MessageEvent) {
        try {
            val messageEventPath: String = p0.path
            if (messageEventPath == pathInicio) {
                Log.d(TAG, "Aplicación abierta en el teléfono")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}
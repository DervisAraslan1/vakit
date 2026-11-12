package dev.vakit.widget

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.flutter.plugin.common.EventChannel

/**
 * Pusula: telefonun baktığı yönü gerçek kuzeye göre derece olarak yayınlar
 * ("vakit/compass" olay kanalı). Yalnızca dinleyen varken sensör açıktır.
 */
class CompassStream(private val ctx: Context) : EventChannel.StreamHandler, SensorEventListener {
    private val sm = ctx.getSystemService(SensorManager::class.java)
    private var sink: EventChannel.EventSink? = null
    private var declination = 0f
    private var accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH

    // Dönme vektörü yoksa ivmeölçer + manyetometre ile hesaplanır.
    private val gravity = FloatArray(3)
    private val magnet = FloatArray(3)
    private var haveGravity = false
    private var haveMagnet = false

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        sink = events
        val (lat, lng) = Store.coords(ctx) ?: Store.city(ctx).let { it.lat to it.lng }
        // Manyetik kuzey ile gerçek kuzey arasındaki fark (Türkiye'de 5-6° doğu).
        declination = GeomagneticField(lat.toFloat(), lng.toFloat(), 0f, System.currentTimeMillis()).declination

        val rotation = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotation != null) {
            sm.registerListener(this, rotation, SensorManager.SENSOR_DELAY_UI)
            // Doğruluk bilgisini manyetometre verir.
            sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        } else {
            val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (acc == null || mag == null) {
                events.error("nosensor", "Bu telefonda pusula sensörü yok", null)
                return
            }
            sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_UI)
            sm.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onCancel(arguments: Any?) {
        sm.unregisterListener(this)
        sink = null
        haveGravity = false
        haveMagnet = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val matrix = FloatArray(9)
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> SensorManager.getRotationMatrixFromVector(matrix, event.values)
            Sensor.TYPE_ACCELEROMETER -> {
                event.values.copyInto(gravity)
                haveGravity = true
                return
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                event.values.copyInto(magnet)
                haveMagnet = true
                if (sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null || !haveGravity) return
                if (!SensorManager.getRotationMatrix(matrix, null, gravity, magnet)) return
            }
            else -> return
        }
        val orientation = FloatArray(3)
        SensorManager.getOrientation(matrix, orientation)
        val heading = (Math.toDegrees(orientation[0].toDouble()) + declination + 360) % 360
        sink?.success(mapOf("heading" to heading, "accuracy" to accuracy))
    }

    override fun onAccuracyChanged(sensor: Sensor, value: Int) {
        if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) accuracy = value
    }
}

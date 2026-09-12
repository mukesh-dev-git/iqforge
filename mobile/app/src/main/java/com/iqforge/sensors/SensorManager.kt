package com.iqforge.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

class SensorManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val proximity: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    var onShakeCallback: (() -> Unit)? = null
    var onTiltScrollCallback: ((Float) -> Unit)? = null
    var onFaceDownCallback: ((Boolean) -> Unit)? = null

    // Shake detection vars
    private var lastUpdate: Long = 0
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var lastZ: Float = 0f
    private val SHAKE_THRESHOLD = 800

    // Tilt-to-scroll: a continuous signal, not a discrete gesture — every sample beyond the
    // deadzone reports a scroll delta for as long as the phone stays tilted, so scroll speed
    // tracks how far it's tilted. Replaces the earlier tilt-left/right sidebar toggle.
    private val TILT_DEADZONE = 0.4f
    private val TILT_SCROLL_SPEED = 28f

    fun startListening() {
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        proximity?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val curTime = System.currentTimeMillis()
                if ((curTime - lastUpdate) > 100) {
                    val diffTime = (curTime - lastUpdate)
                    lastUpdate = curTime

                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]

                    val speed = abs(x + y + z - lastX - lastY - lastZ) / diffTime * 10000

                    if (speed > SHAKE_THRESHOLD) {
                        onShakeCallback?.invoke()
                    }

                    lastX = x
                    lastY = y
                    lastZ = z
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                // Y-axis rotation = tilting the top of the phone forward/back while held
                // upright — forward scrolls down, back scrolls up, same sense as tipping a
                // physical page toward or away from you. Sign convention is untested on real
                // hardware — flip the sign below if forward/back come out swapped once you try it.
                val pitch = event.values[1]
                if (abs(pitch) > TILT_DEADZONE) {
                    onTiltScrollCallback?.invoke(pitch * TILT_SCROLL_SPEED)
                }
            }
            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val isFaceDown = distance < (proximity?.maximumRange ?: 5f).coerceAtMost(3f)
                onFaceDownCallback?.invoke(isFaceDown)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

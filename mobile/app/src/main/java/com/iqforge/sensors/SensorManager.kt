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
    var onTiltRightCallback: (() -> Unit)? = null
    var onTiltLeftCallback: (() -> Unit)? = null
    var onFaceDownCallback: ((Boolean) -> Unit)? = null

    // Shake detection vars
    private var lastUpdate: Long = 0
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var lastZ: Float = 0f
    private val SHAKE_THRESHOLD = 800

    // Tilt left/right: a discrete gesture (open/close the sidebar), not a continuous signal —
    // debounced so one physical tilt fires once instead of on every sensor frame while held.
    private var lastTiltFire: Long = 0
    private val TILT_THRESHOLD = 1.8f
    private val TILT_COOLDOWN_MS = 800L

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
                // Y-axis rotation = rolling the phone left/right while held upright.
                // Sign convention (positive = right) is untested on real hardware — flip the
                // comparison below if right/left come out swapped once you try it.
                val roll = event.values[1]
                val now = System.currentTimeMillis()
                if (now - lastTiltFire > TILT_COOLDOWN_MS) {
                    if (roll > TILT_THRESHOLD) {
                        lastTiltFire = now
                        onTiltRightCallback?.invoke()
                    } else if (roll < -TILT_THRESHOLD) {
                        lastTiltFire = now
                        onTiltLeftCallback?.invoke()
                    }
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

package com.smartwheel.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2

/**
 * Угол руля по датчику GAME_ROTATION_VECTOR (гироскоп + акселерометр, без компаса).
 * Телефон держим вертикально экраном к себе, как руль. Угол = поворот в плоскости экрана.
 */
class SteeringSensor(context: Context) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val r = FloatArray(9)

    val available: Boolean get() = sensor != null

    /** Угол в градусах относительно калибровки, влево/вправо. */
    @Volatile var angle = 0f
    /** Составляющая "вверх" вдоль оси экрана: близко к 0, если телефон вертикален. */
    @Volatile var upZ = 0f
    /** Частота обновления датчика, Гц. */
    @Volatile var hz = 0
    @Volatile var invert = false
    @Volatile var onAngle: ((Float) -> Unit)? = null

    @Volatile private var rawDeg = 0f
    @Volatile private var offset = 0f
    private var count = 0
    private var t0 = System.nanoTime()

    fun start() {
        sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
    }

    fun stop() = sm.unregisterListener(this)

    /** Текущее положение становится нулём. */
    fun calibrate() { offset = rawDeg }

    override fun onSensorChanged(e: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(r, e.values)
        // Вектор "вверх" мира в осях телефона = третья строка матрицы
        val ux = r[6]; val uy = r[7]; val uz = r[8]
        rawDeg = Math.toDegrees(atan2(uy.toDouble(), ux.toDouble())).toFloat()
        upZ = uz

        var a = rawDeg - offset
        while (a > 180f) a -= 360f
        while (a < -180f) a += 360f
        if (invert) a = -a
        angle = a
        onAngle?.invoke(a)

        count++
        val now = System.nanoTime()
        if (now - t0 >= 1_000_000_000L) { hz = count; count = 0; t0 = now }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

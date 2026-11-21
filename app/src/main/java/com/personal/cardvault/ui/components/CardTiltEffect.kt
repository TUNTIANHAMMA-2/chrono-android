package com.personal.cardvault.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 归一化后的设备倾斜角度状态（[-1f, 1f]）：
 * - [pitch]：前后俯仰（抬头/低头倾斜）
 * - [roll]：左右横滚（向左/向右倾斜）
 */
@Immutable
data class CardTiltState(
    val pitch: Float = 0f,
    val roll: Float = 0f
)

/**
 * 将传感器原始弧度转换为相对自然握持基准角的归一化倾斜量（[-1f, 1f]），带微抖动死区。
 */
internal fun normalizeTiltAngles(
    rawPitchRad: Float,
    rawRollRad: Float,
    basePitchRad: Float,
    baseRollRad: Float,
    maxAngleRad: Float = 0.32f, // 约 ±18° 达到自然跟手的 3D 视差倾斜与全息光影
    deadzone: Float = 0.04f
): CardTiltState {
    val dPitch = wrapAngleRad(rawPitchRad - basePitchRad)
    val dRoll = wrapAngleRad(rawRollRad - baseRollRad)
    fun withDeadzone(norm: Float): Float {
        val c = norm.coerceIn(-1f, 1f)
        return when {
            kotlin.math.abs(c) <= deadzone -> 0f
            c > 0f -> (c - deadzone) / (1f - deadzone)
            else -> (c + deadzone) / (1f - deadzone)
        }
    }
    return CardTiltState(
        pitch = withDeadzone(dPitch / maxAngleRad),
        roll = withDeadzone(dRoll / maxAngleRad)
    )
}

internal fun wrapAngleRad(delta: Float): Float {
    var a = delta
    val twoPi = (2.0 * PI).toFloat()
    val pi = PI.toFloat()
    while (a > pi) a -= twoPi
    while (a < -pi) a += twoPi
    return a
}

/**
 * 订阅设备陀螺仪/重力融合传感器，实时驱动卡片 3D 视差倾角与全息反光追踪。
 *
 * - 优先使用 `TYPE_GAME_ROTATION_VECTOR`（陀螺仪+加速度计融合，无磁力计抖动、零漂移）；
 * - 依次回退 `TYPE_ROTATION_VECTOR` → `TYPE_GRAVITY` → `TYPE_ACCELEROMETER`；
 * - 首次采样自动标定用户当前自然握持姿态为零点，并做低通滤波平滑防抖；
 * - 随页面生命周期 `ON_PAUSE`/`onDispose` 自动注销监听，零后台耗电；
 * - 在 Compose Preview 或无传感器设备上安全返回静止零值。
 */
@Composable
fun rememberCardTiltState(enabled: Boolean = true): State<CardTiltState> {
    val state = remember { mutableStateOf(CardTiltState()) }
    val isPreview = LocalInspectionMode.current
    if (!enabled || isPreview) {
        return state
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(context, lifecycleOwner, enabled) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return@DisposableEffect onDispose {}

        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return@DisposableEffect onDispose {}

        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        var hasBaseline = false
        var basePitch = 0f
        var baseRoll = 0f
        var prevRawPitch = 0f
        var prevRawRoll = 0f
        var stillFrames = 0
        var smoothPitch = 0f
        var smoothRoll = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val rawPitch: Float
                val rawRoll: Float
                when (event.sensor.type) {
                    Sensor.TYPE_GAME_ROTATION_VECTOR,
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        rawPitch = orientation[1]
                        rawRoll = orientation[2]
                    }
                    Sensor.TYPE_GRAVITY,
                    Sensor.TYPE_ACCELEROMETER -> {
                        val gx = event.values[0]
                        val gy = event.values[1]
                        val gz = event.values[2]
                        rawPitch = atan2(-gy, sqrt(gx * gx + gz * gz))
                        rawRoll = atan2(gx, sqrt(gy * gy + gz * gz))
                    }
                    else -> return
                }

                if (!hasBaseline) {
                    basePitch = rawPitch
                    baseRoll = rawRoll
                    prevRawPitch = rawPitch
                    prevRawRoll = rawRoll
                    stillFrames = 0
                    hasBaseline = true
                } else {
                    val deltaMotion = kotlin.math.abs(wrapAngleRad(rawPitch - prevRawPitch)) +
                        kotlin.math.abs(wrapAngleRad(rawRoll - prevRawRoll))
                    prevRawPitch = rawPitch
                    prevRawRoll = rawRoll

                    if (deltaMotion < 0.005f) {
                        stillFrames = (stillFrames + 1).coerceAtMost(60)
                    } else {
                        stillFrames = 0
                    }

                    // 静止或放平约 0.35s 后自动加速回正；正在晃动时锁定基准角以保证清晰 3D 视差
                    val recenterRate = if (stillFrames >= 18) 0.065f else 0.002f
                    basePitch = wrapAngleRad(basePitch + wrapAngleRad(rawPitch - basePitch) * recenterRate)
                    baseRoll = wrapAngleRad(baseRoll + wrapAngleRad(rawRoll - baseRoll) * recenterRate)
                }

                val target = normalizeTiltAngles(
                    rawPitchRad = rawPitch,
                    rawRollRad = rawRoll,
                    basePitchRad = basePitch,
                    baseRollRad = baseRoll
                )
                // 低通指数平滑，兼顾跟手灵敏度与防抖
                val alpha = 0.26f
                smoothPitch += (target.pitch - smoothPitch) * alpha
                smoothRoll += (target.roll - smoothRoll) * alpha
                if (kotlin.math.abs(smoothPitch) < 0.005f) smoothPitch = 0f
                if (kotlin.math.abs(smoothRoll) < 0.005f) smoothRoll = 0f
                val prev = state.value
                val changedEnough = kotlin.math.abs(smoothPitch - prev.pitch) >= 0.008f ||
                    kotlin.math.abs(smoothRoll - prev.roll) >= 0.008f ||
                    (smoothPitch == 0f && prev.pitch != 0f) ||
                    (smoothRoll == 0f && prev.roll != 0f)
                if (changedEnough) {
                    state.value = CardTiltState(pitch = smoothPitch, roll = smoothRoll)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        fun register() {
            hasBaseline = false
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }

        fun unregister() {
            sensorManager.unregisterListener(listener)
        }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> register()
                Lifecycle.Event.ON_PAUSE -> unregister()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            register()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            unregister()
        }
    }

    return state
}

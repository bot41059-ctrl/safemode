package com.safemode.antitheft.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.safemode.antitheft.R
import com.safemode.antitheft.ui.MainActivity
import com.safemode.antitheft.ui.TableGuardActivity
import com.safemode.antitheft.util.PreferenceHelper
import kotlin.math.abs
import kotlin.math.sqrt

class SafeModeService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var proximitySensor: Sensor? = null
    private lateinit var prefs: PreferenceHelper
    private var wakeLock: PowerManager.WakeLock? = null

    private var lastTotalAcceleration = 9.8f
    private var isProximityNear = false
    private var isTriggerCooldown = false

    companion object {
        const val CHANNEL_ID = "safe_mode_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.safemode.action.START"
        const val ACTION_STOP = "com.safemode.action.STOP"

        // Threshold: Earth gravity is ~9.8 m/s^2. Pickup motion deviation > 2.5 m/s^2
        private const val MOTION_DELTA_THRESHOLD = 2.4f
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceHelper(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SafeMode::ServiceWakeLock"
        )
        wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24 hours max fallback

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForegroundService()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        registerSensors()
        return START_STICKY
    }

    private fun registerSensors() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (prefs.isPocketModeEnabled) {
            proximitySensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
    }

    private fun unregisterSensors() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !prefs.isArmed || isTriggerCooldown) return

        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                // If value < maximum range, sensor is NEAR (phone is in pocket or facedown)
                val maxRange = event.sensor.maximumRange
                isProximityNear = event.values[0] < maxRange
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val currentTotal = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                val deltaA = abs(currentTotal - lastTotalAcceleration)
                lastTotalAcceleration = currentTotal

                // If Pocket mode is enabled and phone is in pocket, do NOT trigger false alarm
                if (prefs.isPocketModeEnabled && isProximityNear) {
                    return
                }

                // Pickup motion detected!
                if (deltaA > MOTION_DELTA_THRESHOLD) {
                    onMotionDetected()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun onMotionDetected() {
        isTriggerCooldown = true

        // Launch TableGuardActivity / Instant Verification overlay immediately
        val intent = Intent(this, TableGuardActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("EXTRA_AUTO_TRIGGERED", true)
        }
        startActivity(intent)

        // Reset cooldown after 8 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            isTriggerCooldown = false
        }, 8000)
    }

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ Safe Mode is Active")
            .setContentText("Monitoring pickup motion. Phone is armed.")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Safe Mode Security Guard",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground monitoring service for motion detection"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundService() {
        unregisterSensors()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForegroundService()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

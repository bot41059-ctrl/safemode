package com.safemode.antitheft.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.safemode.antitheft.databinding.ActivityTableGuardBinding
import com.safemode.antitheft.util.FaceMatcher
import com.safemode.antitheft.util.PreferenceHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

class TableGuardActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityTableGuardBinding
    private lateinit var prefs: PreferenceHelper
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var proximitySensor: Sensor? = null
    private var imageCapture: ImageCapture? = null

    private var lastTotalAcc = 9.8f
    private var isVerificationInProgress = false
    private var isProximityNear = false
    private var lastTapTime = 0L

    companion object {
        private const val MOTION_THRESHOLD = 2.4f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lock screen wake-up flags (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityTableGuardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceHelper(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        setupClock()
        setupDoubleTapExit()
        startSilentCamera()

        val autoTriggered = intent.getBooleanExtra("EXTRA_AUTO_TRIGGERED", false)
        if (autoTriggered) {
            triggerFaceVerification()
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        if (prefs.isPocketModeEnabled) {
            proximitySensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    private fun setupClock() {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.tvDimClock.text = sdf.format(Date())
    }

    private fun setupDoubleTapExit() {
        binding.tableGuardRoot.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTapTime < 400) {
                Toast.makeText(this, "Table Guard Exited", Toast.LENGTH_SHORT).show()
                finish()
            }
            lastTapTime = now
        }
    }

    private fun startSilentCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture)
            } catch (e: Exception) {
                // ignore
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || isVerificationInProgress) return

        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                isProximityNear = event.values[0] < event.sensor.maximumRange
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (prefs.isPocketModeEnabled && isProximityNear) return

                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val currentTotal = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                val delta = abs(currentTotal - lastTotalAcc)
                lastTotalAcc = currentTotal

                if (delta > MOTION_THRESHOLD) {
                    triggerFaceVerification()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun triggerFaceVerification() {
        if (isVerificationInProgress) return
        isVerificationInProgress = true

        lifecycleScope.launch {
            // Low-light screen flash boost: flash screen white for 300ms if enabled
            if (prefs.isScreenFlashEnabled) {
                binding.viewScreenFlashBoost.visibility = View.VISIBLE
                delay(300)
            }

            captureAndVerify()
        }
    }

    private fun captureAndVerify() {
        val capture = imageCapture
        if (capture == null) {
            launchIntruderAlert(null)
            return
        }

        // Set a 2.5 second timeout for face verification
        val timeoutHandler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (isVerificationInProgress) {
                launchIntruderAlert(null)
            }
        }
        timeoutHandler.postDelayed(timeoutRunnable, 2500)

        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    val bitmap = image.toBitmap()
                    image.close()

                    // Hide flash
                    binding.viewScreenFlashBoost.visibility = View.GONE

                    lifecycleScope.launch {
                        val candidateVector = FaceMatcher.extractFaceVector(bitmap, "Scan")
                        val registeredVectors = prefs.getFaceVectors()

                        val isOwner = candidateVector != null && FaceMatcher.isMatch(candidateVector, registeredVectors)

                        if (isOwner) {
                            // OWNER CONFIRMED! Shant rahe, device unlock
                            isVerificationInProgress = false
                            Toast.makeText(this@TableGuardActivity, "Owner Verified! Welcome back.", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            // INTRUDER DETECTED! Save photo and sound alarm!
                            val photoPath = saveIntruderPhoto(bitmap)
                            launchIntruderAlert(photoPath)
                        }
                    }
                }

                override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    binding.viewScreenFlashBoost.visibility = View.GONE
                    launchIntruderAlert(null)
                }
            }
        )
    }

    private fun saveIntruderPhoto(bitmap: Bitmap): String {
        return try {
            val file = File(filesDir, "intruder_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            ""
        }
    }

    private fun launchIntruderAlert(photoPath: String?) {
        binding.viewScreenFlashBoost.visibility = View.GONE
        val intent = Intent(this, IntruderAlertActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("EXTRA_INTRUDER_PHOTO", photoPath)
        }
        startActivity(intent)
        finish()
    }
}

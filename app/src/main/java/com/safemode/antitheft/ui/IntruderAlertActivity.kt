package com.safemode.antitheft.ui

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.safemode.antitheft.R
import com.safemode.antitheft.databinding.ActivityIntruderAlertBinding
import com.safemode.antitheft.model.IntruderLog
import com.safemode.antitheft.util.FaceMatcher
import com.safemode.antitheft.util.PreferenceHelper
import com.safemode.antitheft.util.SoundManager
import com.safemode.antitheft.util.TelegramNotifier
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IntruderAlertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIntruderAlertBinding
    private lateinit var prefs: PreferenceHelper
    private var imageCapture: ImageCapture? = null
    private var strobeAnimation: AlphaAnimation? = null

    private var initialPhotoPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lock screen wake-up flags
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

        binding = ActivityIntruderAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceHelper(this)
        initialPhotoPath = intent.getStringExtra("EXTRA_INTRUDER_PHOTO")

        setupUI()
        startFlashingStrobe()
        startAlarm()
        startSilentIntruderCamera()

        // Process intruder evidence log & Telegram upload
        handleIntruderEvidence()
    }

    private fun setupUI() {
        binding.tvIntruderWarningMessage.text = prefs.warningMessage

        binding.btnSubmitPin.setOnClickListener {
            val enteredPin = binding.etEmergencyPin.text.toString().trim()
            if (enteredPin == prefs.emergencyPin) {
                disarmAndExit("Alarm Disarmed with Emergency PIN.")
            } else {
                Toast.makeText(this, "Incorrect PIN! Alarm continues.", Toast.LENGTH_SHORT).show()
                binding.etEmergencyPin.text.clear()
            }
        }
    }

    private fun startFlashingStrobe() {
        strobeAnimation = AlphaAnimation(1.0f, 0.0f).apply {
            duration = 180
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        binding.viewStrobeOverlay.startAnimation(strobeAnimation)
    }

    private fun startAlarm() {
        SoundManager.startSiren(this)
    }

    private fun startSilentIntruderCamera() {
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

                // If no initial photo was captured, take one now!
                if (initialPhotoPath.isNullOrEmpty()) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        captureIntruderPhoto()
                    }, 800)
                }

                // Start continuous face scan to disarm if owner looks into camera
                startOwnerFaceScanningLoop()
            } catch (e: Exception) {
                // ignore
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureIntruderPhoto() {
        imageCapture?.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.toBitmap()
                    image.close()
                    initialPhotoPath = saveIntruderBitmap(bitmap)
                    handleIntruderEvidence()
                }

                override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                    // ignore
                }
            }
        )
    }

    private fun startOwnerFaceScanningLoop() {
        val scanHandler = Handler(Looper.getMainLooper())
        val scanRunnable = object : Runnable {
            override fun run() {
                if (SoundManager.isPlaying()) {
                    imageCapture?.takePicture(
                        ContextCompat.getMainExecutor(this@IntruderAlertActivity),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bitmap = image.toBitmap()
                                image.close()
                                lifecycleScope.launch {
                                    val candidate = FaceMatcher.extractFaceVector(bitmap, "AlertScan")
                                    val registered = prefs.getFaceVectors()
                                    if (candidate != null && FaceMatcher.isMatch(candidate, registered)) {
                                        disarmAndExit("Owner Recognized! Safe Mode Disarmed.")
                                    }
                                }
                            }

                            override fun onError(exception: androidx.camera.core.ImageCaptureException) {}
                        }
                    )
                    scanHandler.postDelayed(this, 3000)
                }
            }
        }
        scanHandler.postDelayed(scanRunnable, 3000)
    }

    private fun handleIntruderEvidence() {
        val path = initialPhotoPath ?: return
        val file = File(path)
        if (!file.exists()) return

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = sdf.format(Date())
        val batteryPct = getBatteryPercentage()

        // 1. Save to local Evidence Vault
        val log = IntruderLog(
            id = System.currentTimeMillis().toString(),
            timestamp = timestamp,
            photoPath = path,
            batteryLevel = batteryPct,
            reason = "Unauthorized Phone Pickup"
        )
        prefs.saveIntruderLog(log)

        // 2. Cloud Sync to Telegram Bot (if configured)
        lifecycleScope.launch {
            val caption = "🚨 *INTRUDER ALERT DETECTED!*\n" +
                    "📅 Time: $timestamp\n" +
                    "🔋 Battery: $batteryPct%\n" +
                    "⚠️ Device pickup without face match!\n" +
                    "Message: ${prefs.warningMessage}"
            TelegramNotifier.uploadIntruderPhoto(this@IntruderAlertActivity, file, caption)
        }
    }

    private fun getBatteryPercentage(): Int {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(null, ifilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(null, ifilter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 100
    }

    private fun saveIntruderBitmap(bitmap: Bitmap): String {
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

    private fun disarmAndExit(msg: String) {
        SoundManager.stopSiren()
        strobeAnimation?.cancel()
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        finish()
    }

    // Intercept hardware volume keys to prevent lowering sound
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            return true // block volume change
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true // block back button
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        SoundManager.stopSiren()
        strobeAnimation?.cancel()
    }
}

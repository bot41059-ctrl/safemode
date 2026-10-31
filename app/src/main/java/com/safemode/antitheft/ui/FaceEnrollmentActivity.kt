package com.safemode.antitheft.ui

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.safemode.antitheft.R
import com.safemode.antitheft.databinding.ActivityFaceEnrollmentBinding
import com.safemode.antitheft.model.FaceVector
import com.safemode.antitheft.util.FaceMatcher
import com.safemode.antitheft.util.PreferenceHelper
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceEnrollmentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFaceEnrollmentBinding
    private lateinit var prefs: PreferenceHelper
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private val capturedVectors = mutableListOf<FaceVector>()
    private var currentStep = 1 // 1 = Front, 2 = Left, 3 = Right

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFaceEnrollmentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceHelper(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupUI()
        startCamera()
    }

    private fun setupUI() {
        updateStepUI()

        binding.btnCaptureAngle.setOnClickListener {
            captureCurrentAngle()
        }

        binding.btnResetEnrollment.setOnClickListener {
            capturedVectors.clear()
            currentStep = 1
            updateStepUI()
            prefs.clearFaceVectors()
            Toast.makeText(this, "Face data cleared. Start from Angle 1.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStepUI() {
        when (currentStep) {
            1 -> {
                binding.btnCaptureAngle.text = "CAPTURE ANGLE 1 (LOOK STRAIGHT)"
                binding.tvInstructionStatus.text = "Look straight directly into front camera"
                binding.step1.setBackgroundResource(R.drawable.bg_btn_primary)
                binding.step2.setBackgroundResource(R.drawable.bg_card_cyber)
                binding.step3.setBackgroundResource(R.drawable.bg_card_cyber)
            }
            2 -> {
                binding.btnCaptureAngle.text = "CAPTURE ANGLE 2 (TURN SLIGHTLY LEFT)"
                binding.tvInstructionStatus.text = "Turn your face slightly to the LEFT"
                binding.step1.setBackgroundResource(R.drawable.bg_card_cyber)
                binding.step2.setBackgroundResource(R.drawable.bg_btn_primary)
                binding.step3.setBackgroundResource(R.drawable.bg_card_cyber)
            }
            3 -> {
                binding.btnCaptureAngle.text = "CAPTURE ANGLE 3 (TURN SLIGHTLY RIGHT)"
                binding.tvInstructionStatus.text = "Turn your face slightly to the RIGHT"
                binding.step1.setBackgroundResource(R.drawable.bg_card_cyber)
                binding.step2.setBackgroundResource(R.drawable.bg_card_cyber)
                binding.step3.setBackgroundResource(R.drawable.bg_btn_primary)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to start camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureCurrentAngle() {
        val capture = imageCapture ?: return
        binding.btnCaptureAngle.isEnabled = false
        binding.tvInstructionStatus.text = "Analyzing face..."

        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.toBitmap()
                    image.close()

                    lifecycleScope.launch {
                        val angleLabel = when (currentStep) {
                            1 -> "Front"
                            2 -> "Left"
                            else -> "Right"
                        }

                        val vector = FaceMatcher.extractFaceVector(bitmap, angleLabel)
                        if (vector != null) {
                            capturedVectors.add(vector)
                            Toast.makeText(
                                this@FaceEnrollmentActivity,
                                "Angle $currentStep ($angleLabel) Registered!",
                                Toast.LENGTH_SHORT
                            ).show()

                            if (currentStep < 3) {
                                currentStep++
                                updateStepUI()
                                binding.btnCaptureAngle.isEnabled = true
                            } else {
                                // All 3 angles completed!
                                prefs.saveFaceVectors(capturedVectors)
                                Toast.makeText(
                                    this@FaceEnrollmentActivity,
                                    "Face ID Successfully Enrolled! Safe Mode is ready.",
                                    Toast.LENGTH_LONG
                                ).show()
                                finish()
                            }
                        } else {
                            binding.tvInstructionStatus.text = "No clear face detected! Please hold steady and retry."
                            binding.btnCaptureAngle.isEnabled = true
                        }
                    }
                }

                override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                    binding.tvInstructionStatus.text = "Capture error: ${exception.message}"
                    binding.btnCaptureAngle.isEnabled = true
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

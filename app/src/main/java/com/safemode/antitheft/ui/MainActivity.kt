package com.safemode.antitheft.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.safemode.antitheft.R
import com.safemode.antitheft.databinding.ActivityMainBinding
import com.safemode.antitheft.service.SafeModeService
import com.safemode.antitheft.util.PreferenceHelper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferenceHelper

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        if (!cameraGranted) {
            Toast.makeText(this, "Camera permission is required for face verification!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceHelper(this)

        requestRequiredPermissions()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateUIState()
    }

    private fun setupListeners() {
        // Toggle Safe Mode (Arm / Disarm)
        binding.btnToggleSafeMode.setOnClickListener {
            if (!prefs.isFaceRegistered()) {
                Toast.makeText(this, "Please register your face first before arming!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, FaceEnrollmentActivity::class.java))
                return@setOnClickListener
            }

            prefs.isArmed = !prefs.isArmed
            toggleService(prefs.isArmed)
            updateUIState()
        }

        // Table Guard Mode (Stealth OLED Black Screen)
        binding.btnTableGuardMode.setOnClickListener {
            if (!prefs.isFaceRegistered()) {
                Toast.makeText(this, "Please register your face first!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, FaceEnrollmentActivity::class.java))
                return@setOnClickListener
            }
            startActivity(Intent(this, TableGuardActivity::class.java))
        }

        // Face ID Enrollment
        binding.cardFaceRegistration.setOnClickListener {
            startActivity(Intent(this, FaceEnrollmentActivity::class.java))
        }

        // Intruder Evidence Vault
        binding.cardIntruderLogs.setOnClickListener {
            startActivity(Intent(this, IntruderLogActivity::class.java))
        }

        // Settings Top Button
        binding.btnSettingsTop.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Test Intruder Siren Alert Simulator
        binding.btnSimulateIntruderAlert.setOnClickListener {
            val intent = Intent(this, IntruderAlertActivity::class.java).apply {
                putExtra("EXTRA_SIMULATED", true)
            }
            startActivity(intent)
        }
    }

    private fun toggleService(start: Boolean) {
        val intent = Intent(this, SafeModeService::class.java)
        if (start) {
            intent.action = SafeModeService.ACTION_START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "Safe Mode Armed! Device is protected.", Toast.LENGTH_SHORT).show()
        } else {
            intent.action = SafeModeService.ACTION_STOP
            startService(intent)
            Toast.makeText(this, "Safe Mode Disarmed.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUIState() {
        val isArmed = prefs.isArmed
        if (isArmed) {
            binding.tvGuardStatus.text = "SAFE MODE ACTIVE 🛡️"
            binding.tvGuardStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            binding.tvGuardDescription.text = "Phone is armed. Lifting or moving the device triggers instant verification."
            binding.btnToggleSafeMode.text = "DEACTIVATE SAFE MODE"
            binding.btnToggleSafeMode.setBackgroundResource(R.drawable.bg_btn_danger)
            binding.ivShieldIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent_green))
        } else {
            binding.tvGuardStatus.text = "DISARMED / STANDBY"
            binding.tvGuardStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.tvGuardDescription.text = "Safe Mode is off. Tap below to arm the device."
            binding.btnToggleSafeMode.text = "ACTIVATE SAFE MODE"
            binding.btnToggleSafeMode.setBackgroundResource(R.drawable.bg_btn_primary)
            binding.ivShieldIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent_cyan))
        }

        // Face registration badge
        if (prefs.isFaceRegistered()) {
            binding.tvFaceRegisteredStatus.text = "Registered (${prefs.getFaceVectors().size} angles)"
            binding.tvFaceRegisteredStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
        } else {
            binding.tvFaceRegisteredStatus.text = "Tap to Register Face"
            binding.tvFaceRegisteredStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_orange))
        }

        // Logs count
        val logs = prefs.getIntruderLogs()
        binding.tvLogsCount.text = "${logs.size} captured"
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}

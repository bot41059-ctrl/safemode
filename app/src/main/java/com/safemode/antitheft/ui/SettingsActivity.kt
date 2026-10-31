package com.safemode.antitheft.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.safemode.antitheft.databinding.ActivitySettingsBinding
import com.safemode.antitheft.util.PreferenceHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PreferenceHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceHelper(this)
        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        binding.etSettingsPin.setText(prefs.emergencyPin)
        binding.etSettingsWarningText.setText(prefs.warningMessage)
        binding.switchScreenFlash.isChecked = prefs.isScreenFlashEnabled
        binding.switchPocketMode.isChecked = prefs.isPocketModeEnabled
        binding.etTelegramBotToken.setText(prefs.telegramBotToken)
        binding.etTelegramChatId.setText(prefs.telegramChatId)
    }

    private fun setupListeners() {
        binding.btnSaveSettings.setOnClickListener {
            val newPin = binding.etSettingsPin.text.toString().trim()
            val warningText = binding.etSettingsWarningText.text.toString().trim()

            if (newPin.length < 4) {
                Toast.makeText(this, "Emergency PIN must be at least 4 digits!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.emergencyPin = newPin
            prefs.warningMessage = if (warningText.isNotEmpty()) warningText else PreferenceHelper.DEFAULT_WARNING
            prefs.isScreenFlashEnabled = binding.switchScreenFlash.isChecked
            prefs.isPocketModeEnabled = binding.switchPocketMode.isChecked
            prefs.telegramBotToken = binding.etTelegramBotToken.text.toString().trim()
            prefs.telegramChatId = binding.etTelegramChatId.text.toString().trim()

            Toast.makeText(this, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

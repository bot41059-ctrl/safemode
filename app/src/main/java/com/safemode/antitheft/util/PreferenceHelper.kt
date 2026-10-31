package com.safemode.antitheft.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.safemode.antitheft.model.FaceVector
import com.safemode.antitheft.model.IntruderLog

class PreferenceHelper(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "safe_mode_prefs"
        private const val KEY_ARMED = "key_armed"
        private const val KEY_PIN = "key_pin"
        private const val KEY_WARNING_MSG = "key_warning_msg"
        private const val KEY_SCREEN_FLASH = "key_screen_flash"
        private const val KEY_POCKET_MODE = "key_pocket_mode"
        private const val KEY_TELEGRAM_TOKEN = "key_telegram_token"
        private const val KEY_TELEGRAM_CHAT_ID = "key_telegram_chat_id"
        private const val KEY_FACE_VECTORS = "key_face_vectors"
        private const val KEY_INTRUDER_LOGS = "key_intruder_logs"

        const val DEFAULT_WARNING = "YOU THE FUCKING HELL MAN GO TO HELL"
        const val DEFAULT_PIN = "1234"
    }

    var isArmed: Boolean
        get() = prefs.getBoolean(KEY_ARMED, false)
        set(value) = prefs.edit().putBoolean(KEY_ARMED, value).apply()

    var emergencyPin: String
        get() = prefs.getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN
        set(value) = prefs.edit().putString(KEY_PIN, value).apply()

    var warningMessage: String
        get() = prefs.getString(KEY_WARNING_MSG, DEFAULT_WARNING) ?: DEFAULT_WARNING
        set(value) = prefs.edit().putString(KEY_WARNING_MSG, value).apply()

    var isScreenFlashEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCREEN_FLASH, true)
        set(value) = prefs.edit().putBoolean(KEY_SCREEN_FLASH, value).apply()

    var isPocketModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_POCKET_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_POCKET_MODE, value).apply()

    var telegramBotToken: String
        get() = prefs.getString(KEY_TELEGRAM_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TELEGRAM_TOKEN, value).apply()

    var telegramChatId: String
        get() = prefs.getString(KEY_TELEGRAM_CHAT_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TELEGRAM_CHAT_ID, value).apply()

    fun saveFaceVectors(vectors: List<FaceVector>) {
        val json = gson.toJson(vectors)
        prefs.edit().putString(KEY_FACE_VECTORS, json).apply()
    }

    fun getFaceVectors(): List<FaceVector> {
        val json = prefs.getString(KEY_FACE_VECTORS, null) ?: return emptyList()
        val type = object : TypeToken<List<FaceVector>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isFaceRegistered(): Boolean {
        return getFaceVectors().isNotEmpty()
    }

    fun clearFaceVectors() {
        prefs.edit().remove(KEY_FACE_VECTORS).apply()
    }

    fun saveIntruderLog(log: IntruderLog) {
        val list = getIntruderLogs().toMutableList()
        list.add(0, log) // prepend newest
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_INTRUDER_LOGS, json).apply()
    }

    fun getIntruderLogs(): List<IntruderLog> {
        val json = prefs.getString(KEY_INTRUDER_LOGS, null) ?: return emptyList()
        val type = object : TypeToken<List<IntruderLog>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearIntruderLogs() {
        prefs.edit().remove(KEY_INTRUDER_LOGS).apply()
    }
}

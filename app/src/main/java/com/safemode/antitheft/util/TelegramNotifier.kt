package com.safemode.antitheft.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

object TelegramNotifier {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun uploadIntruderPhoto(
        context: Context,
        photoFile: File,
        captionText: String
    ): Boolean = withContext(Dispatchers.IO) {
        val prefs = PreferenceHelper(context)
        val token = prefs.telegramBotToken.trim()
        val chatId = prefs.telegramChatId.trim()

        if (token.isEmpty() || chatId.isEmpty() || !photoFile.exists()) {
            return@withContext false
        }

        try {
            val url = "https://api.telegram.org/bot$token/sendPhoto"
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("caption", captionText)
                .addFormDataPart(
                    "photo",
                    photoFile.name,
                    photoFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
}

package com.safemode.antitheft.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.concurrent.thread
import kotlin.math.sin

object SoundManager {

    private var isAlarmPlaying = false
    private var audioTrack: AudioTrack? = null
    private var volumeLockHandler: Handler? = null
    private var volumeLockRunnable: Runnable? = null
    private var audioManager: AudioManager? = null
    private var vibrator: Vibrator? = null

    fun startSiren(context: Context) {
        if (isAlarmPlaying) return
        isAlarmPlaying = true

        val appContext = context.applicationContext
        audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        // 1. Force Max Volume Immediately and start continuous 100ms Volume Lock Loop
        lockVolumeToMax()
        startVolumeLockLoop()

        // 2. Start Hardware Level Dual-Tone High-Pitch Siren Waveform
        startSyntheticSiren()

        // 3. Start High Intensity Vibration Pattern
        startVibration()
    }

    fun stopSiren() {
        isAlarmPlaying = false

        // Stop volume lock loop
        volumeLockRunnable?.let { volumeLockHandler?.removeCallbacks(it) }
        volumeLockHandler = null

        // Stop AudioTrack
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // ignore
        }
        audioTrack = null

        // Stop Vibration
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun lockVolumeToMax() {
        audioManager?.let { am ->
            try {
                val maxAlarm = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val maxMusic = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, 0)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun startVolumeLockLoop() {
        volumeLockHandler = Handler(Looper.getMainLooper())
        volumeLockRunnable = object : Runnable {
            override fun run() {
                if (isAlarmPlaying) {
                    lockVolumeToMax()
                    volumeLockHandler?.postDelayed(this, 120)
                }
            }
        }
        volumeLockHandler?.post(volumeLockRunnable!!)
    }

    private fun startSyntheticSiren() {
        thread(start = true, name = "SafeModeSirenThread") {
            val sampleRate = 44100
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(sampleRate / 4)

            try {
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()

                val samples = ShortArray(bufferSize)
                var currentFreq = 1000.0
                var increasing = true
                var phase = 0.0

                while (isAlarmPlaying) {
                    for (i in samples.indices) {
                        // Sweep frequency between 1000 Hz and 2400 Hz for high-pitch piercing siren
                        if (increasing) {
                            currentFreq += 0.08
                            if (currentFreq >= 2400.0) increasing = false
                        } else {
                            currentFreq -= 0.08
                            if (currentFreq <= 1000.0) increasing = true
                        }

                        val angularFreq = 2.0 * Math.PI * currentFreq / sampleRate
                        phase += angularFreq
                        if (phase > 2.0 * Math.PI) {
                            phase -= 2.0 * Math.PI
                        }

                        // Generate dual-tone wave with harsh harmonic
                        val sampleValue = (sin(phase) * 28000.0 + sin(phase * 2.0) * 4000.0).toInt()
                        samples[i] = sampleValue.coerceIn(-32767, 32767).toShort()
                    }

                    audioTrack?.write(samples, 0, samples.size)
                }
            } catch (e: Exception) {
                // track failed
            }
        }
    }

    private fun startVibration() {
        vibrator?.let { vib ->
            try {
                val pattern = longArrayOf(0, 500, 200, 500, 200, 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(pattern, 0)
                }
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun isPlaying(): Boolean = isAlarmPlaying
}

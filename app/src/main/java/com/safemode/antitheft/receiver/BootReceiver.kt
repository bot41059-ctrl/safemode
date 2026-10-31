package com.safemode.antitheft.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.safemode.antitheft.service.SafeModeService
import com.safemode.antitheft.util.PreferenceHelper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PreferenceHelper(context)
            if (prefs.isArmed) {
                val serviceIntent = Intent(context, SafeModeService::class.java).apply {
                    action = SafeModeService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}

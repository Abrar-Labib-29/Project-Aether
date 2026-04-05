package com.floatkey

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "com.floatkey.ACTION_REVIVE") {
            try {
                val serviceIntent = Intent(context, FloatKeyService::class.java)
                context.startForegroundService(serviceIntent)
            } catch (e: Exception) { }
        }
    }
}

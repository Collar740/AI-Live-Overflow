package com.collar740.ailiveoverflow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.collar740.ailiveoverflow.service.PetOverlayService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && context != null) {
            context.startForegroundService(Intent(context, PetOverlayService::class.java))
        }
    }
}

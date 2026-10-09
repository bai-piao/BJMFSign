package com.bjmf.sign.android.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        val store = BjmfStore(appContext)
        BjmfScheduler(appContext).scheduleAll(store.loadTasks())
    }
}

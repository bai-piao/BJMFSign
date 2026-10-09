package com.bjmf.sign.android.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SignAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BjmfScheduler.ACTION_SIGN_TASK) return
        val taskId = intent.getLongExtra(BjmfScheduler.EXTRA_TASK_ID, 0L)
        if (taskId == 0L) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val store = BjmfStore(appContext)
                val scheduler = BjmfScheduler(appContext)
                val service = BjmfNativeService()
                val task = store.findTask(taskId)
                if (task != null && task.enabled) {
                    val result = service.runSign(task)
                    store.appendLog(
                        TaskLog(
                            id = System.currentTimeMillis(),
                            taskId = result.taskId,
                            taskName = result.taskName,
                            runAt = System.currentTimeMillis(),
                            status = result.status,
                            message = result.message,
                        ),
                    )
                    scheduler.schedule(task)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

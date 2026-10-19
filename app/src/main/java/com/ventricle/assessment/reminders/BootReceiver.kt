package com.ventricle.assessment.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ventricle.assessment.data.repository.ReminderRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Re-schedules all future reminders after a device reboot (alarms don't survive it). */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: ReminderRepository

    @Inject
    lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.getUpcoming(System.currentTimeMillis()).forEach {
                    scheduler.schedule(it.id, it.dateTimeMillis)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

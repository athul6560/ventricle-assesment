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

/** Fired by the exact alarm; posts the reminder notification. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: ReminderRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMIND) return
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_REMINDER_ID, -1L)
        if (id == -1L) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val reminder = repository.getById(id)
                if (reminder != null && !reminder.isDone) {
                    ReminderNotifications.show(context, reminder)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

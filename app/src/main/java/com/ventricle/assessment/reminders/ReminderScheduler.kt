package com.ventricle.assessment.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules exact alarms that fire [ReminderReceiver] at the reminder's
 * date/time. Uses setExactAndAllowWhileIdle so the notification shows on
 * the exact date and time even in Doze.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val ACTION_REMIND = "com.ventricle.assessment.ACTION_REMIND"
        const val EXTRA_REMINDER_ID = "reminder_id"
    }

    private val alarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun schedule(id: Long, dateTimeMillis: Long) {
        if (dateTimeMillis <= System.currentTimeMillis()) return
        val pi = pendingIntent(id)
        try {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, dateTimeMillis, pi
                )
            } else {
                // Exact-alarm permission missing: best-effort inexact fallback.
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, dateTimeMillis, pi
                )
            }
        } catch (e: SecurityException) {
            // Permission revoked between check and schedule; nothing to do.
        }
    }

    fun cancel(id: Long) {
        alarmManager.cancel(pendingIntent(id))
    }

    private fun pendingIntent(id: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_REMINDER_ID, id)
            // Unique data URI so each reminder gets a distinct PendingIntent.
            data = Uri.parse("ventricle://reminder/$id")
        }
        return PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

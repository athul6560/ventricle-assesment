package com.ventricle.assessment.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ventricle.assessment.MainActivity
import com.ventricle.assessment.data.local.ReminderEntity

/** Builds and posts reminder notifications on the "reminders" channel. */
object ReminderNotifications {

    const val CHANNEL_ID = "reminders"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Reminders",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Assessment follow-up reminders"
                    }
                )
            }
        }
    }

    fun show(context: Context, reminder: ReminderEntity) {
        createChannel(context)

        val who = buildString {
            if (reminder.patientName.isNotBlank()) {
                append(reminder.patientName)
                if (reminder.mrdNumber.isNotBlank()) append(" (${reminder.mrdNumber})")
            }
        }
        val title = if (who.isNotBlank()) "${reminder.title} · $who" else reminder.title

        val body = buildString {
            if (reminder.notes.isNotBlank()) append(reminder.notes)
            if (reminder.diagnosisNote.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(reminder.diagnosisNote)
            }
        }.ifBlank { "Tap to open" }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", "reminders")
        }
        val contentPi = PendingIntent.getActivity(
            context,
            reminder.id.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()

        NotificationManagerCompat.from(context).notify(reminder.id.toInt(), notification)
    }
}

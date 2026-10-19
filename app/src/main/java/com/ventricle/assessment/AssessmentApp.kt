package com.ventricle.assessment

import android.app.Application
import com.ventricle.assessment.reminders.ReminderNotifications
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AssessmentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.createChannel(this)
    }
}

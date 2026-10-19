package com.ventricle.assessment.data.repository

import com.ventricle.assessment.data.local.ReminderDao
import com.ventricle.assessment.data.local.ReminderEntity
import com.ventricle.assessment.reminders.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepository @Inject constructor(
    private val dao: ReminderDao,
    private val scheduler: ReminderScheduler
) {

    fun observeAll(): Flow<List<ReminderEntity>> = dao.observeAll()

    suspend fun getById(id: Long): ReminderEntity? = dao.getById(id)

    suspend fun getUpcoming(now: Long): List<ReminderEntity> = dao.getUpcoming(now)

    fun canScheduleExactAlarms(): Boolean = scheduler.canScheduleExactAlarms()

    /** Saves and (re)schedules the exact alarm; cancelling it when done or in the past. */
    suspend fun save(reminder: ReminderEntity): Long {
        val id = dao.upsert(reminder)
        val saved = reminder.copy(id = id)
        if (!saved.isDone && saved.dateTimeMillis > System.currentTimeMillis()) {
            scheduler.schedule(id, saved.dateTimeMillis)
        } else {
            scheduler.cancel(id)
        }
        return id
    }

    suspend fun setDone(id: Long, done: Boolean) {
        val current = dao.getById(id) ?: return
        dao.upsert(current.copy(isDone = done))
        if (done) {
            scheduler.cancel(id)
        } else if (current.dateTimeMillis > System.currentTimeMillis()) {
            scheduler.schedule(id, current.dateTimeMillis)
        }
    }

    suspend fun delete(id: Long) {
        scheduler.cancel(id)
        dao.deleteById(id)
    }
}

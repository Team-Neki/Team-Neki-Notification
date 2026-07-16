package com.neki.notification.batch.adapter.`in`.scheduler

import com.neki.notification.batch.adapter.`in`.NotificationJobLauncher
import org.springframework.batch.core.Job
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "neki.batch", name = ["scheduling-enabled"], havingValue = "true")
class NotificationJobScheduler(
    private val launcher: NotificationJobLauncher,
    @Qualifier("weeklyReminderJob") private val weeklyReminderJob: Job,
    @Qualifier("weekendExploreJob") private val weekendExploreJob: Job,
    @Qualifier("holidayExploreJob") private val holidayExploreJob: Job,
) {
    @Scheduled(cron = "\${neki.batch.cron.weekly}", zone = "\${scheduling.zone:Asia/Seoul}")
    fun runWeeklyReminder() {
        launcher.launch(weeklyReminderJob)
    }

    @Scheduled(cron = "\${neki.batch.cron.weekend}", zone = "\${scheduling.zone:Asia/Seoul}")
    fun runWeekendExplore() {
        launcher.launch(weekendExploreJob)
    }

    @Scheduled(cron = "\${neki.batch.cron.holiday}", zone = "\${scheduling.zone:Asia/Seoul}")
    fun runHolidayExplore() {
        launcher.launch(holidayExploreJob)
    }
}

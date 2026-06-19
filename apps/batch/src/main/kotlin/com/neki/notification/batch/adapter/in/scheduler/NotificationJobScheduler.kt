package com.neki.notification.batch.adapter.`in`.scheduler

import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate

@Component
@ConditionalOnProperty(prefix = "neki.batch", name = ["scheduling-enabled"], havingValue = "true")
class NotificationJobScheduler(
    private val jobLauncher: JobLauncher,
    private val jobExplorer: JobExplorer,
    @Qualifier("weeklyReminderJob") private val weeklyReminderJob: Job,
    @Qualifier("weekendExploreJob") private val weekendExploreJob: Job,
    @Qualifier("holidayExploreJob") private val holidayExploreJob: Job,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${neki.batch.cron.weekly}", zone = "\${scheduling.zone:Asia/Seoul}")
    fun runWeeklyReminder() = launch(weeklyReminderJob)

    @Scheduled(cron = "\${neki.batch.cron.weekend}", zone = "\${scheduling.zone:Asia/Seoul}")
    fun runWeekendExplore() = launch(weekendExploreJob)

    @Scheduled(cron = "\${neki.batch.cron.holiday}", zone = "\${scheduling.zone:Asia/Seoul}")
    fun runHolidayExplore() = launch(holidayExploreJob)

    fun launch(job: Job) {
        val running = jobExplorer.findRunningJobExecutions(job.name)
        if (running.isNotEmpty()) {
            log.warn("이전 '{}' 실행이 아직 진행 중이라 이번 트리거를 건너뜀 (running={})", job.name, running.size)
            return
        }
        val businessDate = LocalDate.now(clock)
        val params = JobParametersBuilder()
            .addString("businessDate", businessDate.toString())
            .addLong("launchedAt", clock.millis())
            .toJobParameters()
        jobLauncher.run(job, params)
    }
}

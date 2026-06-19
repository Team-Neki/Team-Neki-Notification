package com.neki.notification.batch.adapter.`in`.scheduler

import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

/**
 * cron 스케줄 → Job 기동 (batch-design §2/§P7).
 *
 *  - WEEKLY_REMINDER: 매일 20:00
 *  - WEEKEND_EXPLORE: 금/토/일 지정 시각
 *  - HOLIDAY_EXPLORE: 매일 깨워 발송일 판정(공휴일 아니면 Job 내부에서 0건 처리)
 *
 * `businessDate`는 운영 타임존 기준 오늘. `launchedAt`(ms)로 JobInstance를 매 기동마다 유일하게 만든다.
 */
@Component
@ConditionalOnProperty(prefix = "neki.batch", name = ["scheduling-enabled"], havingValue = "true")
class NotificationJobScheduler(
    private val jobLauncher: JobLauncher,
    @Qualifier("weeklyReminderJob") private val weeklyReminderJob: Job,
    @Qualifier("weekendExploreJob") private val weekendExploreJob: Job,
    @Qualifier("holidayExploreJob") private val holidayExploreJob: Job,
    private val clock: Clock,
) {

    @Scheduled(cron = "\${neki.batch.cron.weekly}", zone = "\${scheduling.zone:Asia/Seoul}")
    fun runWeeklyReminder() = launch(weeklyReminderJob)

    @Scheduled(cron = "\${neki.batch.cron.weekend}", zone = "\${scheduling.zone:Asia/Seoul}")
    fun runWeekendExplore() = launch(weekendExploreJob)

    @Scheduled(cron = "\${neki.batch.cron.holiday}", zone = "\${scheduling.zone:Asia/Seoul}")
    fun runHolidayExplore() = launch(holidayExploreJob)

    /** 오늘(businessDate) 파라미터로 Job을 기동한다. */
    fun launch(job: Job) {
        val businessDate = LocalDate.now(clock)
        val params = JobParametersBuilder()
            .addString("businessDate", businessDate.toString())
            .addLong("launchedAt", clock.millis())
            .toJobParameters()
        jobLauncher.run(job, params)
    }
}

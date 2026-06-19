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

/**
 * cron 스케줄 → Job 기동 (batch-design §2/§P7).
 *
 *  - WEEKLY_REMINDER: 매일 20:00
 *  - WEEKEND_EXPLORE: 금/토/일 지정 시각
 *  - HOLIDAY_EXPLORE: 매일 깨워 발송일 판정(공휴일 아니면 Job 내부에서 0건 처리)
 *
 * `businessDate`는 운영 타임존 기준 오늘. `launchedAt`(ms)로 JobInstance를 매 기동마다 유일하게 만든다.
 *
 * ## 중복/동시 기동 방어 (C-1)
 * `launchedAt` 유일화 때문에 같은 businessDate라도 매번 새 JobInstance가 생성되므로, Spring Batch의
 * JobInstance 중복 방지에 기댈 수 없다. 그래서 기동 직전 [JobExplorer.findRunningJobExecutions]로
 * **동일 Job이 아직 실행 중이면 이번 트리거를 건너뛴다**(이전 실행이 cron 주기를 넘겨 길어진 경우 방어).
 *
 * 단, 이 가드는 **단일 JVM 인스턴스 내**에서만 유효하다. 본 배치는 단일 인스턴스(예: k8s replica=1
 * 또는 단일 스케줄러 노드) 운영을 전제로 하며, 이 전제는 배포 매니페스트로 강제해야 한다. 다중 인스턴스로
 * 운영해야 한다면 ShedLock 등 분산 락으로 단일 발화를 별도 보장해야 한다(발송 자체의 중복은 DB
 * unique 제약만으로는 막지 못함).
 */
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

    /** 오늘(businessDate) 파라미터로 Job을 기동한다. 동일 Job이 실행 중이면 건너뛴다(C-1). */
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

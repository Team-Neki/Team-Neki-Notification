package com.neki.notification.batch.application.launch

import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

/**
 * 알림 배치 Job 기동 공용 서비스.
 *
 * 스케줄러(NotificationJobScheduler)와 테스트 API(TestNotificationController)가 공유한다.
 * - businessDate / launchedAt 파라미터 구성 (launchedAt 으로 매 기동 JobInstance 유일화)
 * - 동일 Job 이 실행 중이면 기동을 건너뜀 (중복 기동 방어)
 */
@Component
class NotificationJobLauncher(
    private val jobLauncher: JobLauncher,
    private val jobExplorer: JobExplorer,
    jobs: List<Job>,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val jobsByName: Map<String, Job> = jobs.associateBy { it.name }

    val availableJobNames: Set<String>
        get() = jobsByName.keys

    /** 지정 Job 을 운영 타임존 기준 오늘 businessDate 로 기동한다. */
    fun launch(job: Job): JobExecution? = launch(job, LocalDate.now(clock))

    /** 지정 Job 을 businessDate 로 기동한다. 이미 실행 중이면 기동을 건너뛰고 null 을 반환한다. */
    fun launch(job: Job, businessDate: LocalDate): JobExecution? {
        val running = jobExplorer.findRunningJobExecutions(job.name)
        if (running.isNotEmpty()) {
            log.warn("이전 '{}' 실행이 아직 진행 중이라 이번 기동을 건너뜀 (running={})", job.name, running.size)
            return null
        }
        val params = JobParametersBuilder()
            .addString("businessDate", businessDate.toString())
            .addLong("launchedAt", clock.millis())
            .toJobParameters()
        return jobLauncher.run(job, params)
    }

    /**
     * 이름으로 Job 을 찾아 기동한다.
     * @param businessDate null 이면 운영 타임존 기준 오늘.
     * @throws IllegalArgumentException 알 수 없는 Job 이름인 경우.
     */
    fun launchByName(jobName: String, businessDate: LocalDate? = null): JobExecution? {
        val job = jobsByName[jobName]
            ?: throw IllegalArgumentException("알 수 없는 잡 이름: $jobName (가능: $availableJobNames)")
        return launch(job, businessDate ?: LocalDate.now(clock))
    }
}

package com.neki.notification.batch.adapter.`in`.scheduler

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.launch.JobLauncher
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * NotificationJobScheduler 기동 로직 단위 테스트 (Docker 불필요).
 *
 *  - 각 트리거가 해당 Job을 기동
 *  - businessDate = 운영 타임존 기준 오늘 (Asia/Seoul)
 *  - launchedAt 으로 매 기동 JobInstance 유일화
 *  - 동일 Job이 실행 중이면 기동을 건너뜀 (C-1 중복 기동 방어)
 */
class NotificationJobSchedulerTest {

    private val jobLauncher: JobLauncher = mockk(relaxed = true)
    private val jobExplorer: JobExplorer = mockk(relaxed = true) // findRunningJobExecutions 기본 emptySet
    private val weeklyJob: Job = mockk { every { name } returns "weeklyReminderJob" }
    private val weekendJob: Job = mockk { every { name } returns "weekendExploreJob" }
    private val holidayJob: Job = mockk { every { name } returns "holidayExploreJob" }

    // 2026-06-18 11:00 UTC = 2026-06-18 20:00 KST → businessDate 2026-06-18
    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-18T11:00:00Z"), ZoneId.of("Asia/Seoul"))

    private val scheduler =
        NotificationJobScheduler(jobLauncher, jobExplorer, weeklyJob, weekendJob, holidayJob, clock)

    @Test
    fun `runWeeklyReminder 는 weeklyJob 을 오늘 businessDate 로 기동`() {
        val params = slot<JobParameters>()
        every { jobLauncher.run(weeklyJob, capture(params)) } returns mockk()

        scheduler.runWeeklyReminder()

        verify(exactly = 1) { jobLauncher.run(weeklyJob, any()) }
        assertEquals("2026-06-18", params.captured.getString("businessDate"))
        assertEquals(Instant.parse("2026-06-18T11:00:00Z").toEpochMilli(), params.captured.getLong("launchedAt"))
    }

    @Test
    fun `runWeekendExplore 는 weekendJob 을 기동`() {
        scheduler.runWeekendExplore()
        verify(exactly = 1) { jobLauncher.run(weekendJob, any()) }
    }

    @Test
    fun `runHolidayExplore 는 holidayJob 을 기동`() {
        scheduler.runHolidayExplore()
        verify(exactly = 1) { jobLauncher.run(holidayJob, any()) }
    }

    @Test
    fun `이미 실행 중인 Job 이면 기동을 건너뜀`() {
        every { jobExplorer.findRunningJobExecutions("weeklyReminderJob") } returns setOf(mockk<JobExecution>())

        scheduler.runWeeklyReminder()

        verify(exactly = 0) { jobLauncher.run(weeklyJob, any()) }
    }
}

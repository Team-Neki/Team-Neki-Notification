package com.neki.notification.batch.adapter.`in`

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.batch.core.launch.JobLauncher
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * NotificationJobLauncher 기동 로직 단위 테스트 (Docker 불필요).
 *  - businessDate / launchedAt 파라미터 구성
 *  - 동일 Job 실행 중이면 기동 스킵
 *  - 이름으로 기동 / 알 수 없는 이름 예외
 */
class NotificationJobLauncherTest {

    private val jobLauncher: JobLauncher = mockk(relaxed = true)
    private val jobExplorer: JobExplorer = mockk(relaxed = true) // findRunningJobExecutions 기본 emptySet
    private val weeklyJob: Job = mockk { every { name } returns "weeklyReminderJob" }
    private val weekendJob: Job = mockk { every { name } returns "weekendExploreJob" }

    // 2026-06-18 11:00 UTC = 2026-06-18 20:00 KST → businessDate 2026-06-18
    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-18T11:00:00Z"), ZoneId.of("Asia/Seoul"))

    private val launcher = NotificationJobLauncher(jobLauncher, jobExplorer, listOf(weeklyJob, weekendJob), clock)

    @Test
    fun `launch 는 기본 businessDate(오늘) 와 launchedAt 으로 기동`() {
        val params = slot<JobParameters>()
        every { jobLauncher.run(weeklyJob, capture(params)) } returns mockk()

        launcher.launch(weeklyJob)

        verify(exactly = 1) { jobLauncher.run(weeklyJob, any()) }
        assertEquals("2026-06-18", params.captured.getString("businessDate"))
        assertEquals(Instant.parse("2026-06-18T11:00:00Z").toEpochMilli(), params.captured.getLong("launchedAt"))
    }

    @Test
    fun `launch 는 명시한 businessDate 를 사용`() {
        val params = slot<JobParameters>()
        every { jobLauncher.run(weeklyJob, capture(params)) } returns mockk()

        launcher.launch(weeklyJob, LocalDate.parse("2026-01-15"))

        assertEquals("2026-01-15", params.captured.getString("businessDate"))
    }

    @Test
    fun `이미 실행 중인 Job 이면 기동을 건너뛰고 null 반환`() {
        every { jobExplorer.findRunningJobExecutions("weeklyReminderJob") } returns setOf(mockk<JobExecution>())

        val result = launcher.launch(weeklyJob)

        assertNull(result)
        verify(exactly = 0) { jobLauncher.run(weeklyJob, any()) }
    }

    @Test
    fun `launchByName 은 이름으로 Job 을 찾아 기동`() {
        launcher.launchByName("weekendExploreJob")
        verify(exactly = 1) { jobLauncher.run(weekendJob, any()) }
    }

    @Test
    fun `launchByName 은 알 수 없는 이름이면 예외`() {
        assertThrows(IllegalArgumentException::class.java) {
            launcher.launchByName("unknownJob")
        }
    }

    @Test
    fun `availableJobNames 는 주입된 Job 이름 집합`() {
        assertEquals(setOf("weeklyReminderJob", "weekendExploreJob"), launcher.availableJobNames)
    }
}

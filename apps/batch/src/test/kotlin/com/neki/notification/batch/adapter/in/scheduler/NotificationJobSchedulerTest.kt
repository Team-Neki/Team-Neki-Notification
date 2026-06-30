package com.neki.notification.batch.adapter.`in`.scheduler

import com.neki.notification.batch.application.launch.NotificationJobLauncher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.batch.core.Job

/**
 * NotificationJobScheduler 단위 테스트 (Docker 불필요).
 * 각 트리거가 해당 Job 으로 NotificationJobLauncher 에 위임하는지만 검증한다.
 * (businessDate/launchedAt 구성·중복 기동 방어는 NotificationJobLauncherTest 가 담당)
 */
class NotificationJobSchedulerTest {

    private val launcher: NotificationJobLauncher = mockk(relaxed = true)
    private val weeklyJob: Job = mockk { every { name } returns "weeklyReminderJob" }
    private val weekendJob: Job = mockk { every { name } returns "weekendExploreJob" }
    private val holidayJob: Job = mockk { every { name } returns "holidayExploreJob" }

    private val scheduler = NotificationJobScheduler(launcher, weeklyJob, weekendJob, holidayJob)

    @Test
    fun `runWeeklyReminder 는 weeklyJob 으로 위임`() {
        scheduler.runWeeklyReminder()
        verify(exactly = 1) { launcher.launch(weeklyJob) }
    }

    @Test
    fun `runWeekendExplore 는 weekendJob 으로 위임`() {
        scheduler.runWeekendExplore()
        verify(exactly = 1) { launcher.launch(weekendJob) }
    }

    @Test
    fun `runHolidayExplore 는 holidayJob 으로 위임`() {
        scheduler.runHolidayExplore()
        verify(exactly = 1) { launcher.launch(holidayJob) }
    }
}

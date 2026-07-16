package com.neki.notification.batch.adapter.`in`.web

import com.neki.notification.application.port.out.PushSender
import com.neki.notification.batch.application.launch.NotificationJobLauncher
import com.neki.notification.domain.model.FcmResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.JobExecution
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * TestNotificationController 단위 테스트 (standalone MockMvc, Docker/Spring 컨텍스트 불필요).
 */
class TestNotificationControllerTest {

    private val launcher: NotificationJobLauncher = mockk()
    private val pushSender: PushSender = mockk()
    private val mockMvc: MockMvc =
        MockMvcBuilders.standaloneSetup(TestNotificationController(launcher, pushSender)).build()

    @Test
    fun `정상 잡 트리거는 200 과 실행 정보를 반환`() {
        val execution: JobExecution = mockk {
            every { id } returns 42L
            every { status } returns BatchStatus.COMPLETED
            every { exitStatus } returns ExitStatus.COMPLETED
        }
        every { launcher.launchByName("weeklyReminderJob", null) } returns execution

        mockMvc.perform(post("/test/notifications/jobs/{jobName}", "weeklyReminderJob"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.jobName").value("weeklyReminderJob"))
            .andExpect(jsonPath("$.executionId").value(42))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
    }

    @Test
    fun `알 수 없는 잡 이름은 400`() {
        every { launcher.launchByName("nope", null) } throws IllegalArgumentException("알 수 없는 잡 이름: nope")

        mockMvc.perform(post("/test/notifications/jobs/{jobName}", "nope"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value("UNKNOWN_JOB"))
    }

    @Test
    fun `이미 실행 중이면 409`() {
        every { launcher.launchByName("weeklyReminderJob", null) } returns null

        mockMvc.perform(post("/test/notifications/jobs/{jobName}", "weeklyReminderJob"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.status").value("ALREADY_RUNNING"))
    }

    @Test
    fun `businessDate 파라미터를 launcher 로 전달`() {
        val execution: JobExecution = mockk {
            every { id } returns 1L
            every { status } returns BatchStatus.COMPLETED
            every { exitStatus } returns ExitStatus.COMPLETED
        }
        every { launcher.launchByName("weeklyReminderJob", java.time.LocalDate.parse("2026-01-15")) } returns execution

        mockMvc.perform(post("/test/notifications/jobs/{jobName}", "weeklyReminderJob").param("businessDate", "2026-01-15"))
            .andExpect(status().isOk)

        verify { launcher.launchByName("weeklyReminderJob", java.time.LocalDate.parse("2026-01-15")) }
    }

    @Test
    fun `단건 푸시는 발송 결과를 반환`() {
        every { pushSender.send(any(), any()) } returns FcmResult.SUCCESS

        mockMvc.perform(
            post("/test/notifications/push")
                .param("token", "device-token")
                .param("title", "제목")
                .param("body", "본문"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").value("device-token"))
            .andExpect(jsonPath("$.result").value("SUCCESS"))

        verify { pushSender.send("device-token", any()) }
    }
}

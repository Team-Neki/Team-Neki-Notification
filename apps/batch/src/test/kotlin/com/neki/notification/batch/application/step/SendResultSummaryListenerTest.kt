package com.neki.notification.batch.application.step

import com.neki.notification.application.port.out.NotificationLogStore
import com.neki.notification.domain.model.NotificationLog
import com.neki.notification.domain.model.NotificationStatus
import com.neki.notification.domain.model.NotificationType
import org.junit.jupiter.api.Test
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.StepExecution
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SendResultSummaryListenerTest {

    private fun stepExecution(businessDate: String?): StepExecution {
        val builder = JobParametersBuilder()
        if (businessDate != null) builder.addString("businessDate", businessDate)
        val jobExecution = JobExecution(1L, builder.toJobParameters())
        return StepExecution("weekendStep", jobExecution)
    }

    private fun logStore(counts: Map<NotificationStatus, Long>) = object : NotificationLogStore {
        override fun alreadySent(userId: Long, type: NotificationType, businessDate: LocalDate) = false
        override fun save(log: NotificationLog) = Unit
        override fun countByStatus(type: NotificationType, businessDate: LocalDate) = counts
    }

    @Test
    fun `스텝 종료 시 status 별 집계를 실행 컨텍스트에 기록한다`() {
        val store = logStore(
            mapOf(
                NotificationStatus.SENT to 3L,
                NotificationStatus.FAILED to 2L,
                NotificationStatus.DEAD to 1L,
            ),
        )
        val listener = SendResultSummaryListener(NotificationType.WEEKEND_EXPLORE, store)
        val execution = stepExecution("2026-07-13")

        listener.afterStep(execution)

        val ctx = execution.executionContext
        assertEquals(6L, ctx.getLong("notif.total"))
        assertEquals(3L, ctx.getLong("notif.sent"))
        assertEquals(2L, ctx.getLong("notif.failed"))
        assertEquals(1L, ctx.getLong("notif.dead"))
        assertEquals(0L, ctx.getLong("notif.skipped")) // 집계에 없는 상태는 0
    }

    @Test
    fun `businessDate 파라미터가 없으면 집계하지 않고 종료한다`() {
        val store = logStore(mapOf(NotificationStatus.SENT to 1L))
        val listener = SendResultSummaryListener(NotificationType.WEEKEND_EXPLORE, store)
        val execution = stepExecution(null)

        listener.afterStep(execution)

        assertFalse(execution.executionContext.containsKey("notif.total"))
    }
}

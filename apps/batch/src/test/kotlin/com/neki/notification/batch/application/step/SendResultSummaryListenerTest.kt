package com.neki.notification.batch.application.step

import com.neki.notification.application.port.out.NotificationLogStore
import com.neki.notification.domain.model.FcmResult
import com.neki.notification.domain.model.NotificationLog
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

    private fun logStore(counts: Map<FcmResult, Long>) = object : NotificationLogStore {
        override fun alreadySent(userId: Long, type: NotificationType, businessDate: LocalDate) = false
        override fun save(log: NotificationLog) = Unit
        override fun countByResult(type: NotificationType, businessDate: LocalDate) = counts
    }

    @Test
    fun `스텝 종료 시 FcmResult 별 집계를 실행 컨텍스트에 기록한다`() {
        val store = logStore(mapOf(FcmResult.SUCCESS to 3L, FcmResult.FAILED to 2L))
        val listener = SendResultSummaryListener(NotificationType.WEEKEND_EXPLORE, store)
        val execution = stepExecution("2026-07-13")

        listener.afterStep(execution)

        val ctx = execution.executionContext
        assertEquals(5L, ctx.getLong("fcm.total"))
        assertEquals(3L, ctx.getLong("fcm.success"))
        assertEquals(2L, ctx.getLong("fcm.failed"))
        assertEquals(0L, ctx.getLong("fcm.skipped")) // 집계에 없는 결과는 0
    }

    @Test
    fun `businessDate 파라미터가 없으면 집계하지 않고 종료한다`() {
        val store = logStore(mapOf(FcmResult.SUCCESS to 1L))
        val listener = SendResultSummaryListener(NotificationType.WEEKEND_EXPLORE, store)
        val execution = stepExecution(null)

        listener.afterStep(execution)

        assertFalse(execution.executionContext.containsKey("fcm.total"))
    }
}

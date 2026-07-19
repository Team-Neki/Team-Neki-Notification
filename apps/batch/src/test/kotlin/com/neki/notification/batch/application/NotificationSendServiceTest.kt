package com.neki.notification.batch.application

import com.neki.notification.application.port.out.NotificationHistStore
import com.neki.notification.application.port.out.NotificationLogStore
import com.neki.notification.application.port.out.PushSender
import com.neki.notification.domain.model.FcmResult
import com.neki.notification.domain.model.MessageTone
import com.neki.notification.domain.model.NotificationType
import com.neki.notification.domain.model.PreparedNotification
import com.neki.notification.domain.model.RenderedMessage
import com.neki.notification.domain.model.SendTarget
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * `dispatch`의 hist 적재 규칙 단위 검증(H-3): 성공 건만 hist에 남기고, hist 실패는 best-effort로
 * 삼켜 발송/`notification_log`를 지킨다. 발송 결과별 분기라 DB 없이 mockk로 포트를 스텁한다.
 */
class NotificationSendServiceTest {

    private val logStore = mockk<NotificationLogStore>(relaxed = true)
    private val pushSender = mockk<PushSender>()
    private val histStore = mockk<NotificationHistStore>(relaxed = true)
    private val service = NotificationSendService(logStore, pushSender, histStore)

    private val prepared = PreparedNotification(
        target = SendTarget(userId = 7L, fcmToken = "tok-7"),
        type = NotificationType.WEEKEND_EXPLORE,
        message = RenderedMessage(
            title = "주말엔 무얼 남겨볼까요?",
            body = "가까운 곳부터 둘러보세요.",
            actualTone = MessageTone.INFORMATIVE,
            variableApplied = false,
        ),
        businessDate = LocalDate.of(2026, 6, 20),
    )

    @Test
    fun `발송 성공이면 notification_log와 hist 모두 적재한다`() {
        every { pushSender.send(any(), any()) } returns FcmResult.SUCCESS

        service.dispatch(prepared)

        verify(exactly = 1) { logStore.save(any()) }
        verify(exactly = 1) {
            histStore.save(
                userId = 7L,
                type = NotificationType.WEEKEND_EXPLORE,
                title = "주말엔 무얼 남겨볼까요?",
                body = "가까운 곳부터 둘러보세요.",
                link = null,
            )
        }
    }

    @Test
    fun `발송 실패(FAILED)면 notification_log만 적재하고 hist는 남기지 않는다`() {
        every { pushSender.send(any(), any()) } returns FcmResult.FAILED

        service.dispatch(prepared)

        verify(exactly = 1) { logStore.save(any()) }
        verify(exactly = 0) { histStore.save(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `발송 생략(SKIPPED)이면 hist를 남기지 않는다`() {
        every { pushSender.send(any(), any()) } returns FcmResult.SKIPPED

        service.dispatch(prepared)

        verify(exactly = 0) { histStore.save(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `hist 적재가 실패해도 예외를 전파하지 않는다(best-effort)`() {
        every { pushSender.send(any(), any()) } returns FcmResult.SUCCESS
        every { histStore.save(any(), any(), any(), any(), any()) } throws RuntimeException("의도된 hist 적재 실패")

        // 예외가 밖으로 나오지 않아야 한다. notification_log는 이미 적재된 상태.
        service.dispatch(prepared)

        verify(exactly = 1) { logStore.save(any()) }
    }
}

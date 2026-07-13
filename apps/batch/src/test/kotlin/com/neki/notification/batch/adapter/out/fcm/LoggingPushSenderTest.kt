package com.neki.notification.batch.adapter.out.fcm

import com.neki.notification.domain.model.MessageTone
import com.neki.notification.domain.model.NotificationStatus
import com.neki.notification.domain.model.RenderedMessage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * LoggingPushSender 는 항상 발송을 생략하고 SKIPPED 를 반환한다 (FCM 비활성 환경).
 */
class LoggingPushSenderTest {

    @Test
    fun `발송하지 않고 SKIPPED 반환`() {
        val result = LoggingPushSender().send(
            "tok",
            RenderedMessage("제목", "본문", MessageTone.FRIENDLY, variableApplied = false),
        )
        assertEquals(NotificationStatus.SKIPPED, result)
    }
}

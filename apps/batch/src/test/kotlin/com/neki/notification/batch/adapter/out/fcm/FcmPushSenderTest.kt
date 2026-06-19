package com.neki.notification.batch.adapter.out.fcm

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.neki.notification.domain.model.FcmResult
import com.neki.notification.domain.model.MessageTone
import com.neki.notification.domain.model.RenderedMessage
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * FcmPushSender 발송 결과 매핑 단위 테스트 (Docker 불필요 — FirebaseMessaging 모킹).
 */
class FcmPushSenderTest {

    private val messaging: FirebaseMessaging = mockk()
    private val sender = FcmPushSender(messaging)
    private val message = RenderedMessage("제목", "본문", MessageTone.FRIENDLY, variableApplied = false)

    @Test
    fun `전송 성공이면 SUCCESS`() {
        every { messaging.send(any<Message>()) } returns "projects/x/messages/1"
        assertEquals(FcmResult.SUCCESS, sender.send("tok", message))
    }

    @Test
    fun `전송 중 예외가 나면 FAILED 로 흡수`() {
        every { messaging.send(any<Message>()) } throws RuntimeException("boom")
        assertEquals(FcmResult.FAILED, sender.send("tok", message))
    }
}

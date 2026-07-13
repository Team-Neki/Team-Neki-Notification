package com.neki.notification.batch.adapter.out.fcm

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.neki.notification.domain.model.MessageTone
import com.neki.notification.domain.model.NotificationStatus
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
    fun `전송 성공이면 SENT`() {
        every { messaging.send(any<Message>()) } returns "projects/x/messages/1"
        assertEquals(NotificationStatus.SENT, sender.send("tok", message))
    }

    @Test
    fun `일반 예외는 일시 실패 FAILED 로 흡수`() {
        every { messaging.send(any<Message>()) } throws RuntimeException("boom")
        assertEquals(NotificationStatus.FAILED, sender.send("tok", message))
    }

    // 실패 분류는 순수 함수로 검증한다(FirebaseMessagingException 은 생성/모킹이 취약해 직접 만들지 않는다).
    @Test
    fun `영구 실패 코드는 DEAD 로 분류`() {
        assertEquals(NotificationStatus.DEAD, FcmPushSender.statusFor(MessagingErrorCode.UNREGISTERED))
        assertEquals(NotificationStatus.DEAD, FcmPushSender.statusFor(MessagingErrorCode.INVALID_ARGUMENT))
        assertEquals(NotificationStatus.DEAD, FcmPushSender.statusFor(MessagingErrorCode.SENDER_ID_MISMATCH))
        assertEquals(NotificationStatus.DEAD, FcmPushSender.statusFor(MessagingErrorCode.THIRD_PARTY_AUTH_ERROR))
    }

    @Test
    fun `일시적 오류 코드와 null 은 FAILED 로 분류`() {
        assertEquals(NotificationStatus.FAILED, FcmPushSender.statusFor(MessagingErrorCode.UNAVAILABLE))
        assertEquals(NotificationStatus.FAILED, FcmPushSender.statusFor(MessagingErrorCode.INTERNAL))
        assertEquals(NotificationStatus.FAILED, FcmPushSender.statusFor(MessagingErrorCode.QUOTA_EXCEEDED))
        assertEquals(NotificationStatus.FAILED, FcmPushSender.statusFor(null))
    }
}

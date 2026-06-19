package com.neki.notification.domain.service

import com.neki.notification.domain.model.MessageTone
import com.neki.notification.domain.model.MessageVariable
import com.neki.notification.domain.model.NotificationType
import com.neki.notification.domain.model.SendDecision
import com.neki.notification.domain.model.SendTarget
import com.neki.notification.domain.model.SkipReason
import com.neki.notification.domain.policy.ToneAssignmentPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * NotificationProcessor (batch-design §5): ①동의 → ②중복 → ③톤배정+렌더링 순서 판정.
 *
 * 분기 전수 (P2 인수조건):
 *  - 미동의 → Skip(NO_CONSENT)  (동의 검사가 중복보다 먼저: 미동의+중복이어도 NO_CONSENT)
 *  - 동의+중복 → Skip(ALREADY_SENT)
 *  - 동의+미중복+변수無 → Send (기본 문구)
 *  - 동의+미중복+변수有 → Send (개인화, variableApplied=true)
 *  - 동의+미중복+변수필요한데 값 없음 → Send (폴백 톤, variableApplied=false)
 */
class NotificationProcessorTest {

    private fun target(
        userId: Long,
        consent: Boolean,
        variables: Map<MessageVariable, String?> = emptyMap(),
    ) = SendTarget(
        userId = userId,
        fcmToken = "token-$userId",
        pushConsent = consent,
        variables = variables,
    )

    @Test
    fun `미동의면 NO_CONSENT 로 스킵`() {
        val decision = NotificationProcessor.decide(
            target = target(userId = 1L, consent = false),
            type = NotificationType.WEEKEND_EXPLORE,
            alreadySent = false,
        )
        assertEquals(SendDecision.Skip(SkipReason.NO_CONSENT), decision)
    }

    @Test
    fun `미동의는 중복 여부보다 우선한다`() {
        val decision = NotificationProcessor.decide(
            target = target(userId = 1L, consent = false),
            type = NotificationType.WEEKEND_EXPLORE,
            alreadySent = true,
        )
        assertEquals(SendDecision.Skip(SkipReason.NO_CONSENT), decision)
    }

    @Test
    fun `동의했지만 이미 발송됐으면 ALREADY_SENT 로 스킵`() {
        val decision = NotificationProcessor.decide(
            target = target(userId = 1L, consent = true),
            type = NotificationType.WEEKEND_EXPLORE,
            alreadySent = true,
        )
        assertEquals(SendDecision.Skip(SkipReason.ALREADY_SENT), decision)
    }

    @Test
    fun `동의+미중복+변수없는 알림이면 기본 문구로 발송`() {
        // WEEKEND_EXPLORE 는 변수 없음. userId=0 -> INFORMATIVE 톤.
        val decision = NotificationProcessor.decide(
            target = target(userId = 0L, consent = true),
            type = NotificationType.WEEKEND_EXPLORE,
            alreadySent = false,
        )
        val send = assertIs<SendDecision.Send>(decision)
        assertEquals(MessageTone.INFORMATIVE, send.message.actualTone)
        assertEquals("주말 전 포토부스 확인하기", send.message.title)
        assertEquals(false, send.message.variableApplied)
    }

    @Test
    fun `톤은 ToneAssignmentPolicy 와 일치한다`() {
        // userId=2 -> SUGGESTIVE. WEEKEND_EXPLORE SUGGESTIVE 는 변수 없음.
        val userId = 2L
        val decision = NotificationProcessor.decide(
            target = target(userId = userId, consent = true),
            type = NotificationType.WEEKEND_EXPLORE,
            alreadySent = false,
        )
        val send = assertIs<SendDecision.Send>(decision)
        assertEquals(ToneAssignmentPolicy.assign(userId), send.message.actualTone)
    }

    @Test
    fun `변수값이 있으면 개인화 문구로 발송`() {
        // userId=2 -> SUGGESTIVE. WEEKLY_REMINDER SUGGESTIVE 는 [최근 업로드 요일] 필요.
        val decision = NotificationProcessor.decide(
            target = target(
                userId = 2L,
                consent = true,
                variables = mapOf(MessageVariable.RECENT_UPLOAD_DAY to "지난 토요일"),
            ),
            type = NotificationType.WEEKLY_REMINDER,
            alreadySent = false,
        )
        val send = assertIs<SendDecision.Send>(decision)
        assertEquals(MessageTone.SUGGESTIVE, send.message.actualTone)
        assertTrue(send.message.variableApplied)
        assertEquals("지난 토요일처럼 오늘도 남겨볼까요?", send.message.title)
    }

    @Test
    fun `변수값이 없으면 폴백 톤 기본 문구로 발송`() {
        // userId=2 -> SUGGESTIVE 필요변수 없음 -> WEEKLY_REMINDER 폴백 톤 INFORMATIVE.
        val decision = NotificationProcessor.decide(
            target = target(userId = 2L, consent = true, variables = emptyMap()),
            type = NotificationType.WEEKLY_REMINDER,
            alreadySent = false,
        )
        val send = assertIs<SendDecision.Send>(decision)
        assertEquals(MessageTone.INFORMATIVE, send.message.actualTone)
        assertEquals(false, send.message.variableApplied)
        assertEquals("일주일 전 사진이 있어요", send.message.title)
    }
}

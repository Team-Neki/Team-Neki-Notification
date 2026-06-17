package com.neki.notification.domain.service

import com.neki.notification.domain.model.MessageTone
import com.neki.notification.domain.model.MessageVariable
import com.neki.notification.domain.model.NotificationType
import com.neki.notification.domain.model.SendTarget
import org.junit.jupiter.api.Nested
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * NotificationProcessor.process 인수조건.
 *
 * 판정 순서(명세): ① 동의 재확인 → ② 중복 체크 → ③ 톤 배정 + 렌더링 (skip-early).
 *
 * 설계 메모: process는 순수 함수다. dedup 조회는 NotificationLogRepository(포트)의
 * 책임이며 그 결과(alreadySent)를 입력으로 주입받는다 — 그래서 이 테스트에는 mock이 없다.
 * 톤 배정/렌더링은 ToneAssignmentPolicy·MessageRenderer에 위임하므로, 여기서는 그
 * 위임 결과가 ProcessOutcome에 올바르게 반영되는지를 검증한다.
 *
 * 톤 배정: MessageTone.entries[floorMod(userId, 3)]
 *   userId 0 -> INFORMATIVE, 1 -> FRIENDLY, 2 -> SUGGESTIVE.
 */
class NotificationProcessorTest {

    private fun target(
        userId: Long,
        pushConsent: Boolean = true,
        variables: Map<MessageVariable, String?> = emptyMap(),
    ) = SendTarget(
        userId = userId,
        fcmToken = "token-$userId",
        pushConsent = pushConsent,
        variables = variables,
    )

    // =====================================================================================
    // A1 / A2 / A7: Skip 판정과 그 우선순위 (동의 먼저, 그다음 중복).
    // =====================================================================================

    @Nested
    inner class SkipDecisions {

        @Test
        fun `A1 - skips with NOT_CONSENTED when push consent is false`() {
            val outcome = NotificationProcessor.process(
                target(userId = 2L, pushConsent = false),
                NotificationType.WEEKLY_REMINDER,
                alreadySent = false,
            )

            val skip = assertIs<ProcessOutcome.Skip>(outcome)
            assertEquals(SkipReason.NOT_CONSENTED, skip.reason)
        }

        @Test
        fun `A2 - skips with ALREADY_SENT when consented but already sent`() {
            val outcome = NotificationProcessor.process(
                target(userId = 2L, pushConsent = true),
                NotificationType.WEEKLY_REMINDER,
                alreadySent = true,
            )

            val skip = assertIs<ProcessOutcome.Skip>(outcome)
            assertEquals(SkipReason.ALREADY_SENT, skip.reason)
        }

        @Test
        fun `A7 - consent is checked before dedup, so NOT_CONSENTED wins when both apply`() {
            val outcome = NotificationProcessor.process(
                target(userId = 2L, pushConsent = false),
                NotificationType.WEEKLY_REMINDER,
                alreadySent = true,
            )

            val skip = assertIs<ProcessOutcome.Skip>(outcome)
            assertEquals(
                SkipReason.NOT_CONSENTED,
                skip.reason,
                "Consent must be evaluated before dedup (judgement order ①→②)",
            )
        }
    }

    // =====================================================================================
    // A3 / A4 / A5: 동의 + 미발송에서 변수가 필요한 톤(SUGGESTIVE/WEEKLY)의 치환·폴백.
    // =====================================================================================

    @Nested
    inner class SendWithVariableBearingTone {

        // userId=2 -> SUGGESTIVE; WEEKLY_REMINDER/SUGGESTIVE 템플릿은 {최근 업로드 요일} 필요.

        @Test
        fun `A3 - sends with variable substituted when value is present`() {
            val outcome = NotificationProcessor.process(
                target(
                    userId = 2L,
                    variables = mapOf(MessageVariable.RECENT_UPLOAD_DAY to "금요일"),
                ),
                NotificationType.WEEKLY_REMINDER,
                alreadySent = false,
            )

            val send = assertIs<ProcessOutcome.Send>(outcome)
            assertEquals(MessageTone.SUGGESTIVE, send.assignedTone)
            assertEquals(MessageTone.SUGGESTIVE, send.message.actualTone)
            assertTrue(send.message.variableApplied)
            assertTrue(
                send.message.title.contains("금요일"),
                "substituted title should contain the variable value: ${send.message.title}",
            )
        }

        @Test
        fun `A4 - sends with fallback tone when required variable is absent`() {
            val outcome = NotificationProcessor.process(
                target(userId = 2L, variables = emptyMap()),
                NotificationType.WEEKLY_REMINDER,
                alreadySent = false,
            )

            val send = assertIs<ProcessOutcome.Send>(outcome)
            // assignedTone reflects the user's deterministic tone (pre-fallback).
            assertEquals(MessageTone.SUGGESTIVE, send.assignedTone)
            // actualTone falls back to WEEKLY_REMINDER.fallbackTone = INFORMATIVE.
            assertEquals(MessageTone.INFORMATIVE, send.message.actualTone)
            assertFalse(send.message.variableApplied)
        }

        @Test
        fun `A5 - sends with fallback tone when required variable is blank whitespace`() {
            val outcome = NotificationProcessor.process(
                target(
                    userId = 2L,
                    variables = mapOf(MessageVariable.RECENT_UPLOAD_DAY to "  "),
                ),
                NotificationType.WEEKLY_REMINDER,
                alreadySent = false,
            )

            val send = assertIs<ProcessOutcome.Send>(outcome)
            assertEquals(MessageTone.SUGGESTIVE, send.assignedTone)
            assertEquals(MessageTone.INFORMATIVE, send.message.actualTone)
            assertFalse(send.message.variableApplied)
        }
    }

    // =====================================================================================
    // A6: 변수가 필요 없는 톤(INFORMATIVE/WEEKLY)은 템플릿 그대로, 폴백 없음.
    // =====================================================================================

    @Test
    fun `A6 - sends template verbatim for a tone that needs no variable`() {
        val outcome = NotificationProcessor.process(
            target(userId = 0L), // floorMod(0,3)=0 -> INFORMATIVE
            NotificationType.WEEKLY_REMINDER,
            alreadySent = false,
        )

        val send = assertIs<ProcessOutcome.Send>(outcome)
        assertEquals(MessageTone.INFORMATIVE, send.assignedTone)
        assertEquals(MessageTone.INFORMATIVE, send.message.actualTone)
        assertFalse(send.message.variableApplied)
        assertEquals("일주일 전 사진이 있어요", send.message.title)
        assertEquals("네키에 저장한 네컷을 다시 확인해보세요.", send.message.body)
    }

    // =====================================================================================
    // A8: 톤 배정 결정성 — userId 0/1/2 및 음수 경계.
    // =====================================================================================

    @Nested
    inner class DeterministicToneAssignment {

        @Test
        fun `A8 - userId 0 assigns INFORMATIVE`() {
            assertAssignedTone(userId = 0L, expected = MessageTone.INFORMATIVE)
        }

        @Test
        fun `A8 - userId 1 assigns FRIENDLY`() {
            assertAssignedTone(userId = 1L, expected = MessageTone.FRIENDLY)
        }

        @Test
        fun `A8 - userId 2 assigns SUGGESTIVE`() {
            assertAssignedTone(userId = 2L, expected = MessageTone.SUGGESTIVE)
        }

        @Test
        fun `A8 - negative userId -1 wraps via floorMod to SUGGESTIVE`() {
            assertAssignedTone(userId = -1L, expected = MessageTone.SUGGESTIVE)
        }

        // WEEKEND_EXPLORE has no variables for any tone, so assignedTone is observable
        // directly without any fallback interfering.
        private fun assertAssignedTone(userId: Long, expected: MessageTone) {
            val outcome = NotificationProcessor.process(
                target(userId = userId),
                NotificationType.WEEKEND_EXPLORE,
                alreadySent = false,
            )

            val send = assertIs<ProcessOutcome.Send>(outcome)
            assertEquals(expected, send.assignedTone)
            assertEquals(expected, send.message.actualTone, "WEEKEND_EXPLORE never falls back")
        }
    }

    // =====================================================================================
    // A9: 타입 교차 커버리지 — HOLIDAY_EXPLORE/INFORMATIVE 는 {공휴일명} 필요.
    //     값 있으면 치환, 없으면 SUGGESTIVE로 폴백.
    // =====================================================================================

    @Nested
    inner class HolidayExploreCrossCoverage {

        // userId=0 -> INFORMATIVE; HOLIDAY_EXPLORE/INFORMATIVE 템플릿은 {공휴일명} 필요.

        @Test
        fun `A9 - HOLIDAY_EXPLORE substitutes holiday name when present`() {
            val outcome = NotificationProcessor.process(
                target(
                    userId = 0L,
                    variables = mapOf(MessageVariable.HOLIDAY_NAME to "어린이날"),
                ),
                NotificationType.HOLIDAY_EXPLORE,
                alreadySent = false,
            )

            val send = assertIs<ProcessOutcome.Send>(outcome)
            assertEquals(MessageTone.INFORMATIVE, send.assignedTone)
            assertEquals(MessageTone.INFORMATIVE, send.message.actualTone)
            assertTrue(send.message.variableApplied)
            assertTrue(
                send.message.title.contains("어린이날"),
                "substituted title should contain the holiday name: ${send.message.title}",
            )
        }

        @Test
        fun `A9 - HOLIDAY_EXPLORE falls back to SUGGESTIVE when holiday name missing`() {
            val outcome = NotificationProcessor.process(
                target(userId = 0L, variables = emptyMap()),
                NotificationType.HOLIDAY_EXPLORE,
                alreadySent = false,
            )

            val send = assertIs<ProcessOutcome.Send>(outcome)
            assertEquals(MessageTone.INFORMATIVE, send.assignedTone)
            // HOLIDAY_EXPLORE.fallbackTone = SUGGESTIVE.
            assertEquals(MessageTone.SUGGESTIVE, send.message.actualTone)
            assertFalse(send.message.variableApplied)
        }

        // userId=1 -> FRIENDLY; HOLIDAY_EXPLORE/FRIENDLY 템플릿도 {공휴일명} 필요.

        @Test
        fun `A9 - HOLIDAY_EXPLORE FRIENDLY substitutes holiday name when present`() {
            val outcome = NotificationProcessor.process(
                target(
                    userId = 1L,
                    variables = mapOf(MessageVariable.HOLIDAY_NAME to "어린이날"),
                ),
                NotificationType.HOLIDAY_EXPLORE,
                alreadySent = false,
            )

            val send = assertIs<ProcessOutcome.Send>(outcome)
            assertEquals(MessageTone.FRIENDLY, send.assignedTone)
            assertEquals(MessageTone.FRIENDLY, send.message.actualTone)
            assertTrue(send.message.variableApplied)
            assertTrue(
                send.message.title.contains("어린이날"),
                "substituted title should contain the holiday name: ${send.message.title}",
            )
        }

        @Test
        fun `A9 - HOLIDAY_EXPLORE FRIENDLY falls back to SUGGESTIVE when holiday name missing`() {
            val outcome = NotificationProcessor.process(
                target(userId = 1L, variables = emptyMap()),
                NotificationType.HOLIDAY_EXPLORE,
                alreadySent = false,
            )

            val send = assertIs<ProcessOutcome.Send>(outcome)
            assertEquals(MessageTone.FRIENDLY, send.assignedTone)
            assertEquals(MessageTone.SUGGESTIVE, send.message.actualTone)
            assertFalse(send.message.variableApplied)
        }
    }
}

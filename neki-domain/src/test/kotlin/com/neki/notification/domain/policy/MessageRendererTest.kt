package com.neki.notification.domain.policy

import com.neki.notification.domain.model.MessageTone
import com.neki.notification.domain.model.MessageVariable
import com.neki.notification.domain.model.NotificationType
import org.junit.jupiter.api.Nested
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MessageRenderer tests (templates + substitution/fallback rules).
 *
 * String literals are copied VERBATIM from notification-copy-spec.md.
 * Watch the Korean spacing and the trailing "!" in HOLIDAY_EXPLORE / FRIENDLY body.
 */
class MessageRendererTest {

    // =====================================================================================
    // #3: All 9 (type, tone) combinations render the exact title/body for the variable-free
    //     / default render. For templates that need no variable: variableApplied=false,
    //     actualTone=assignedTone. (Variable-bearing templates are exercised in #4/#5.)
    // =====================================================================================

    @Nested
    inner class VariableFreeTemplates {

        // --- WEEKLY_REMINDER ---------------------------------------------------------------

        @Test
        fun `WEEKLY_REMINDER INFORMATIVE`() {
            val result = MessageRenderer.render(
                NotificationType.WEEKLY_REMINDER,
                MessageTone.INFORMATIVE,
                emptyMap(),
            )
            assertEquals("일주일 전 사진이 있어요", result.title)
            assertEquals("네키에 저장한 네컷을 다시 확인해보세요.", result.body)
            assertEquals(MessageTone.INFORMATIVE, result.actualTone)
            assertFalse(result.variableApplied)
        }

        @Test
        fun `WEEKLY_REMINDER FRIENDLY`() {
            val result = MessageRenderer.render(
                NotificationType.WEEKLY_REMINDER,
                MessageTone.FRIENDLY,
                emptyMap(),
            )
            assertEquals("벌써 일주일 전 네컷이에요", result.title)
            assertEquals("지난 사진을 네키에서 다시 꺼내보세요.", result.body)
            assertEquals(MessageTone.FRIENDLY, result.actualTone)
            assertFalse(result.variableApplied)
        }

        // WEEKLY_REMINDER / SUGGESTIVE requires a variable -> covered in the happy and fallback cases.

        // --- WEEKEND_EXPLORE (no variables at all) ---------------------------

        @Test
        fun `WEEKEND_EXPLORE INFORMATIVE`() {
            val result = MessageRenderer.render(
                NotificationType.WEEKEND_EXPLORE,
                MessageTone.INFORMATIVE,
                emptyMap(),
            )
            assertEquals("주말 전 포토부스 확인하기", result.title)
            assertEquals("가까운 포토부스를 네키 지도에서 확인해보세요.", result.body)
            assertEquals(MessageTone.INFORMATIVE, result.actualTone)
            assertFalse(result.variableApplied)
        }

        @Test
        fun `WEEKEND_EXPLORE FRIENDLY`() {
            val result = MessageRenderer.render(
                NotificationType.WEEKEND_EXPLORE,
                MessageTone.FRIENDLY,
                emptyMap(),
            )
            assertEquals("이번 주말엔 어디서 찍을까요?", result.title)
            assertEquals("약속 전에 근처 포토부스를 미리 찾아보세요.", result.body)
            assertEquals(MessageTone.FRIENDLY, result.actualTone)
            assertFalse(result.variableApplied)
        }

        @Test
        fun `WEEKEND_EXPLORE SUGGESTIVE`() {
            val result = MessageRenderer.render(
                NotificationType.WEEKEND_EXPLORE,
                MessageTone.SUGGESTIVE,
                emptyMap(),
            )
            assertEquals("약속 전에 미리 찾아보세요", result.title)
            assertEquals("가까운 포토부스를 네키 지도에서 확인해보세요.", result.body)
            assertEquals(MessageTone.SUGGESTIVE, result.actualTone)
            assertFalse(result.variableApplied)
        }

        // --- HOLIDAY_EXPLORE ---------------------------------------------------------------

        // HOLIDAY_EXPLORE / INFORMATIVE & FRIENDLY require [공휴일명] -> covered in #4/#5.

        @Test
        fun `HOLIDAY_EXPLORE SUGGESTIVE`() {
            val result = MessageRenderer.render(
                NotificationType.HOLIDAY_EXPLORE,
                MessageTone.SUGGESTIVE,
                emptyMap(),
            )
            assertEquals("쉬는 날 가기 좋은 포토부스", result.title)
            assertEquals("네키 지도에서 가까운 포토부스를 확인해보세요.", result.body)
            assertEquals(MessageTone.SUGGESTIVE, result.actualTone)
            assertFalse(result.variableApplied)
        }
    }

    // =====================================================================================
    // #4: Variable substitution (happy path) -> variableApplied=true, actualTone=assignedTone
    // =====================================================================================

    @Nested
    inner class VariableSubstitutionHappyPath {

        @Test
        fun `WEEKLY_REMINDER SUGGESTIVE substitutes recent upload day`() {
            val result = MessageRenderer.render(
                NotificationType.WEEKLY_REMINDER,
                MessageTone.SUGGESTIVE,
                mapOf(MessageVariable.RECENT_UPLOAD_DAY to "지난 토요일"),
            )
            assertEquals("지난 토요일처럼 오늘도 남겨볼까요?", result.title)
            assertEquals("오늘 찍은 사진도 네키에 정리해보세요.", result.body)
            assertEquals(MessageTone.SUGGESTIVE, result.actualTone)
            assertTrue(result.variableApplied)
        }

        @Test
        fun `HOLIDAY_EXPLORE INFORMATIVE substitutes holiday name`() {
            val result = MessageRenderer.render(
                NotificationType.HOLIDAY_EXPLORE,
                MessageTone.INFORMATIVE,
                mapOf(MessageVariable.HOLIDAY_NAME to "어린이날"),
            )
            assertEquals("어린이날 포토부스 확인하기", result.title)
            assertEquals("쉬는 날 방문할 포토부스를 네키 지도에서 확인해보세요.", result.body)
            assertEquals(MessageTone.INFORMATIVE, result.actualTone)
            assertTrue(result.variableApplied)
        }

        @Test
        fun `HOLIDAY_EXPLORE FRIENDLY substitutes holiday name (note trailing exclamation in body)`() {
            val result = MessageRenderer.render(
                NotificationType.HOLIDAY_EXPLORE,
                MessageTone.FRIENDLY,
                mapOf(MessageVariable.HOLIDAY_NAME to "어린이날"),
            )
            assertEquals("어린이날에 약속 있으신가요?", result.title)
            assertEquals("약속 전에 근처 포토부스를 미리 확인해보세요!", result.body)
            assertEquals(MessageTone.FRIENDLY, result.actualTone)
            assertTrue(result.variableApplied)
        }
    }

    // =====================================================================================
    // Fallback when required variable missing.
    //     Cover all "value absent" forms: key absent, null, "", "   ".
    // =====================================================================================

    @Nested
    inner class FallbackOnMissingVariable {

        // WEEKLY_REMINDER + SUGGESTIVE -> fallback to WEEKLY_REMINDER.fallbackTone = INFORMATIVE.

        @Test
        fun `WEEKLY_REMINDER SUGGESTIVE falls back when key absent`() {
            assertWeeklyReminderFallback(emptyMap())
        }

        @Test
        fun `WEEKLY_REMINDER SUGGESTIVE falls back when value is null`() {
            assertWeeklyReminderFallback(mapOf(MessageVariable.RECENT_UPLOAD_DAY to null))
        }

        @Test
        fun `WEEKLY_REMINDER SUGGESTIVE falls back when value is empty string`() {
            assertWeeklyReminderFallback(mapOf(MessageVariable.RECENT_UPLOAD_DAY to ""))
        }

        @Test
        fun `WEEKLY_REMINDER SUGGESTIVE falls back when value is whitespace`() {
            assertWeeklyReminderFallback(mapOf(MessageVariable.RECENT_UPLOAD_DAY to "   "))
        }

        private fun assertWeeklyReminderFallback(variables: Map<MessageVariable, String?>) {
            val result = MessageRenderer.render(
                NotificationType.WEEKLY_REMINDER,
                MessageTone.SUGGESTIVE,
                variables,
            )
            // Falls back to the INFORMATIVE (variable-free) template.
            assertEquals("일주일 전 사진이 있어요", result.title)
            assertEquals("네키에 저장한 네컷을 다시 확인해보세요.", result.body)
            assertEquals(MessageTone.INFORMATIVE, result.actualTone)
            assertFalse(result.variableApplied)
        }

        // HOLIDAY_EXPLORE + INFORMATIVE -> fallback to HOLIDAY_EXPLORE.fallbackTone = SUGGESTIVE.

        @Test
        fun `HOLIDAY_EXPLORE INFORMATIVE falls back when key absent`() {
            assertHolidayInformativeFallback(emptyMap())
        }

        @Test
        fun `HOLIDAY_EXPLORE INFORMATIVE falls back when value is null`() {
            assertHolidayInformativeFallback(mapOf(MessageVariable.HOLIDAY_NAME to null))
        }

        @Test
        fun `HOLIDAY_EXPLORE INFORMATIVE falls back when value is empty string`() {
            assertHolidayInformativeFallback(mapOf(MessageVariable.HOLIDAY_NAME to ""))
        }

        @Test
        fun `HOLIDAY_EXPLORE INFORMATIVE falls back when value is whitespace`() {
            assertHolidayInformativeFallback(mapOf(MessageVariable.HOLIDAY_NAME to "   "))
        }

        private fun assertHolidayInformativeFallback(variables: Map<MessageVariable, String?>) {
            val result = MessageRenderer.render(
                NotificationType.HOLIDAY_EXPLORE,
                MessageTone.INFORMATIVE,
                variables,
            )
            // Falls back to the SUGGESTIVE (variable-free) template.
            assertEquals("쉬는 날 가기 좋은 포토부스", result.title)
            assertEquals("네키 지도에서 가까운 포토부스를 확인해보세요.", result.body)
            assertEquals(MessageTone.SUGGESTIVE, result.actualTone)
            assertFalse(result.variableApplied)
        }

        // HOLIDAY_EXPLORE + FRIENDLY also requires [공휴일명] -> must fall back to SUGGESTIVE too.
        @Test
        fun `HOLIDAY_EXPLORE FRIENDLY falls back to SUGGESTIVE when holiday name missing`() {
            val result = MessageRenderer.render(
                NotificationType.HOLIDAY_EXPLORE,
                MessageTone.FRIENDLY,
                mapOf(MessageVariable.HOLIDAY_NAME to null),
            )
            assertEquals("쉬는 날 가기 좋은 포토부스", result.title)
            assertEquals("네키 지도에서 가까운 포토부스를 확인해보세요.", result.body)
            assertEquals(MessageTone.SUGGESTIVE, result.actualTone)
            assertFalse(result.variableApplied)
        }
    }

    // =====================================================================================
    // #6: WEEKEND_EXPLORE never uses variables even if the variables map is populated.
    // =====================================================================================

    @Nested
    inner class WeekendExploreIgnoresVariables {

        // Data-driven across all 3 tones (see deviation note: junit-jupiter-params is not on
        // the test compile classpath, so @EnumSource cannot be used).
        @Test
        fun `WEEKEND_EXPLORE never applies variables for any tone`() {
            // A fully populated map with both variables must be ignored for every tone.
            val populated = mapOf(
                MessageVariable.RECENT_UPLOAD_DAY to "지난 토요일",
                MessageVariable.HOLIDAY_NAME to "어린이날",
            )

            for (tone in MessageTone.entries) {
                val result = MessageRenderer.render(NotificationType.WEEKEND_EXPLORE, tone, populated)

                assertFalse(result.variableApplied, "WEEKEND_EXPLORE has no variables: tone=$tone")
                assertEquals(tone, result.actualTone, "no fallback should occur for WEEKEND_EXPLORE: tone=$tone")

                val (expectedTitle, expectedBody) = when (tone) {
                    MessageTone.INFORMATIVE -> "주말 전 포토부스 확인하기" to "가까운 포토부스를 네키 지도에서 확인해보세요."
                    MessageTone.FRIENDLY -> "이번 주말엔 어디서 찍을까요?" to "약속 전에 근처 포토부스를 미리 찾아보세요."
                    MessageTone.SUGGESTIVE -> "약속 전에 미리 찾아보세요" to "가까운 포토부스를 네키 지도에서 확인해보세요."
                }
                assertEquals(expectedTitle, result.title, "tone=$tone")
                assertEquals(expectedBody, result.body, "tone=$tone")
            }
        }
    }
}

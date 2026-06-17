package com.neki.notification.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Acceptance criterion #1 + #7.
 *
 * Covers:
 *  - MessageTone declaration / entries order (load-bearing for tone assignment).
 *  - NotificationType.fallbackTone mapping.
 *  - MessageVariable token strings.
 *  - SendTarget construction & default empty variables map.
 */
class EnumContractTest {

    // --- #1: MessageTone order ---------------------------------------------------------------

    @Test
    fun `MessageTone entries order is exactly INFORMATIVE, FRIENDLY, SUGGESTIVE`() {
        assertEquals(
            listOf(MessageTone.INFORMATIVE, MessageTone.FRIENDLY, MessageTone.SUGGESTIVE),
            MessageTone.entries,
            "Declaration order is significant: tone assignment indexes MessageTone.entries by floorMod(userId, 3)",
        )
    }

    // --- #1: NotificationType.fallbackTone ---------------------------------------------------

    @Test
    fun `WEEKLY_REMINDER fallbackTone is INFORMATIVE`() {
        assertEquals(MessageTone.INFORMATIVE, NotificationType.WEEKLY_REMINDER.fallbackTone)
    }

    @Test
    fun `WEEKEND_EXPLORE fallbackTone is INFORMATIVE`() {
        assertEquals(MessageTone.INFORMATIVE, NotificationType.WEEKEND_EXPLORE.fallbackTone)
    }

    @Test
    fun `HOLIDAY_EXPLORE fallbackTone is SUGGESTIVE`() {
        assertEquals(MessageTone.SUGGESTIVE, NotificationType.HOLIDAY_EXPLORE.fallbackTone)
    }

    // --- MessageVariable token strings ----------------------------------------

    @Test
    fun `MessageVariable tokens match copy-spec`() {
        assertEquals("최근 업로드 요일", MessageVariable.RECENT_UPLOAD_DAY.token)
        assertEquals("공휴일명", MessageVariable.HOLIDAY_NAME.token)
    }

    // --- #7: SendTarget ----------------------------------------------------------------------

    @Test
    fun `SendTarget defaults to empty variables map`() {
        val target = SendTarget(
            userId = 42L,
            fcmToken = "token-abc",
            pushConsent = true,
        )

        assertEquals(42L, target.userId)
        assertEquals("token-abc", target.fcmToken)
        assertTrue(target.pushConsent)
        assertTrue(target.variables.isEmpty(), "variables must default to an empty map")
    }

    @Test
    fun `SendTarget retains supplied variables`() {
        val vars = mapOf(MessageVariable.RECENT_UPLOAD_DAY to "지난 토요일")
        val target = SendTarget(
            userId = 7L,
            fcmToken = "token-xyz",
            pushConsent = false,
            variables = vars,
        )

        assertEquals(vars, target.variables)
        assertEquals("지난 토요일", target.variables[MessageVariable.RECENT_UPLOAD_DAY])
        assertEquals(false, target.pushConsent)
    }
}

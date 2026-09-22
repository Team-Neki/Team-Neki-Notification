package com.neki.notification.domain.policy

import com.neki.notification.domain.model.MessageTone
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * docs/prd/notification-copy-spec.md §5: tone = MessageTone.entries[ floorMod(userId + businessDate.toEpochDay(), 3) ]
 *   0 -> INFORMATIVE, 1 -> FRIENDLY, 2 -> SUGGESTIVE
 * 같은 유저라도 발송일이 바뀌면 톤이 순환한다. userId 는 Long, 음수도 floorMod 로 안전 처리.
 */
class ToneAssignmentPolicyTest {

    /** epochDay = 0 이라 톤이 userId % 3 과 같아 손으로 검산하기 쉽다. */
    private val epochDay0 = LocalDate.of(1970, 1, 1)

    @Test
    fun `같은 유저라도 발송일이 하루씩 지나면 톤이 순환한다`() {
        assertEquals(MessageTone.INFORMATIVE, ToneAssignmentPolicy.assign(0L, epochDay0))
        assertEquals(MessageTone.FRIENDLY, ToneAssignmentPolicy.assign(0L, epochDay0.plusDays(1)))
        assertEquals(MessageTone.SUGGESTIVE, ToneAssignmentPolicy.assign(0L, epochDay0.plusDays(2)))
        assertEquals(MessageTone.INFORMATIVE, ToneAssignmentPolicy.assign(0L, epochDay0.plusDays(3)))
    }

    @Test
    fun `같은 날에는 userId 가 1 늘 때마다 톤이 한 칸씩 밀린다`() {
        val cases: List<Pair<Long, MessageTone>> = listOf(
            0L to MessageTone.INFORMATIVE,
            1L to MessageTone.FRIENDLY,
            2L to MessageTone.SUGGESTIVE,
            3L to MessageTone.INFORMATIVE,
            999_999_999_999L to MessageTone.INFORMATIVE, // floorMod = 0
            999_999_999_998L to MessageTone.SUGGESTIVE, // floorMod = 2
        )
        for ((userId, expected) in cases) {
            assertEquals(expected, ToneAssignmentPolicy.assign(userId, epochDay0), "userId=$userId")
        }
    }

    @Test
    fun `실제 날짜도 epochDay 를 더해 계산한다`() {
        // 2026-09-23 의 epochDay = 20719, floorMod(20719, 3) = 1
        val date = LocalDate.of(2026, 9, 23)
        assertEquals(20719L, date.toEpochDay())
        assertEquals(MessageTone.FRIENDLY, ToneAssignmentPolicy.assign(0L, date))
        assertEquals(MessageTone.SUGGESTIVE, ToneAssignmentPolicy.assign(1L, date))
        assertEquals(MessageTone.INFORMATIVE, ToneAssignmentPolicy.assign(2L, date))
    }

    @Test
    fun `userId 가 Long 최대값이어도 덧셈 오버플로 없이 수학적 합의 mod 를 따른다`() {
        // floorMod(Long.MAX_VALUE, 3) = 1, epochDay 1 -> (1 + 1) % 3 = 2 -> SUGGESTIVE.
        // 그냥 더하면 Long.MIN_VALUE 로 감싸져 floorMod = 1 -> FRIENDLY 가 나온다.
        assertEquals(MessageTone.SUGGESTIVE, ToneAssignmentPolicy.assign(Long.MAX_VALUE, epochDay0.plusDays(1)))
    }

    @Test
    fun `음수 userId 도 floorMod 로 안전하게 처리한다`() {
        // naive % 라면 -1 % 3 = -1 로 entries[-1] 에서 죽는다.
        val cases: List<Pair<Long, MessageTone>> = listOf(
            -1L to MessageTone.SUGGESTIVE,
            -2L to MessageTone.FRIENDLY,
            -3L to MessageTone.INFORMATIVE,
            -4L to MessageTone.SUGGESTIVE,
        )
        for ((userId, expected) in cases) {
            assertEquals(expected, ToneAssignmentPolicy.assign(userId, epochDay0), "negative userId=$userId")
        }
    }
}

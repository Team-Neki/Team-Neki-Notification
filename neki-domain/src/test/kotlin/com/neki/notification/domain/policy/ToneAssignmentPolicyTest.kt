package com.neki.notification.domain.policy

import com.neki.notification.domain.model.MessageTone
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Acceptance criterion #2: ToneAssignmentPolicy.assign.
 *
 * copy-spec §5: tone = MessageTone.entries[ Math.floorMod(userId, 3) ]
 *   floorMod == 0 -> INFORMATIVE, 1 -> FRIENDLY, 2 -> SUGGESTIVE
 * userId is Long; negative userIds must use floorMod (no crash, no negative index).
 *
 * NOTE: implemented as data-driven @Test loops rather than @ParameterizedTest because
 * junit-jupiter-params is NOT on the domain test COMPILE classpath (only junit-jupiter-api
 * is, via kotlin-test-junit5). See the deviation note in the deliverable.
 */
class ToneAssignmentPolicyTest {

    @Test
    fun `assign maps userId to tone via floorMod for representative ids`() {
        val cases: List<Pair<Long, MessageTone>> = listOf(
            // representative non-negatives 0,1,2,3
            0L to MessageTone.INFORMATIVE,
            1L to MessageTone.FRIENDLY,
            2L to MessageTone.SUGGESTIVE,
            3L to MessageTone.INFORMATIVE,
            4L to MessageTone.FRIENDLY,
            5L to MessageTone.SUGGESTIVE,
            // large values
            999_999_999_999L to MessageTone.INFORMATIVE, // floorMod = 0
            999_999_999_998L to MessageTone.SUGGESTIVE,  // floorMod = 2
        )

        for ((userId, expected) in cases) {
            assertEquals(expected, ToneAssignmentPolicy.assign(userId), "userId=$userId")
        }
    }

    @Test
    fun `assign handles negative userId via floorMod (no crash, no negative index)`() {
        // A naive userId % 3 would yield -1 for userId=-1 and crash on entries[-1].
        // floorMod(-1,3)=2 -> SUGGESTIVE, floorMod(-2,3)=1 -> FRIENDLY, floorMod(-3,3)=0 -> INFORMATIVE.
        val cases: List<Pair<Long, MessageTone>> = listOf(
            -1L to MessageTone.SUGGESTIVE,
            -2L to MessageTone.FRIENDLY,
            -3L to MessageTone.INFORMATIVE,
            -4L to MessageTone.SUGGESTIVE,
        )

        for ((userId, expected) in cases) {
            assertEquals(expected, ToneAssignmentPolicy.assign(userId), "negative userId=$userId")
        }
    }
}

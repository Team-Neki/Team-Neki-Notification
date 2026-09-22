package com.neki.notification.domain.policy

import com.neki.notification.domain.model.MessageTone
import java.time.LocalDate

object ToneAssignmentPolicy {
    fun assign(userId: Long, businessDate: LocalDate): MessageTone =
        MessageTone.entries[Math.floorMod(userId + businessDate.toEpochDay(), 3)]
}

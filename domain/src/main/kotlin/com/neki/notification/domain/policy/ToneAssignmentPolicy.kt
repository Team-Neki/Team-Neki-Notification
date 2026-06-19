package com.neki.notification.domain.policy

import com.neki.notification.domain.model.MessageTone

object ToneAssignmentPolicy {
    fun assign(userId: Long): MessageTone =
        MessageTone.entries[Math.floorMod(userId, 3)]
}

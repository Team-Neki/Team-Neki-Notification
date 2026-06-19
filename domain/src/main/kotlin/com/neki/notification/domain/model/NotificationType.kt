package com.neki.notification.domain.model

enum class NotificationType {
    WEEKLY_REMINDER,
    WEEKEND_EXPLORE,
    HOLIDAY_EXPLORE,
}

val NotificationType.fallbackTone: MessageTone
    get() = when (this) {
        NotificationType.WEEKLY_REMINDER -> MessageTone.INFORMATIVE
        NotificationType.WEEKEND_EXPLORE -> MessageTone.INFORMATIVE
        NotificationType.HOLIDAY_EXPLORE -> MessageTone.SUGGESTIVE
    }

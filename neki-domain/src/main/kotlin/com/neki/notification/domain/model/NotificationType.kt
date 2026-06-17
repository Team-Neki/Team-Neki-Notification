package com.neki.notification.domain.model

/**
 * In-scope 알림 타입 (copy-spec §1).
 */
enum class NotificationType {
    WEEKLY_REMINDER,
    WEEKEND_EXPLORE,
    HOLIDAY_EXPLORE,
}

/**
 * 알림 타입별 기본(폴백) 톤 (copy-spec §1). 폴백 톤 템플릿은 변수를 필요로 하지 않는다.
 */
val NotificationType.fallbackTone: MessageTone
    get() = when (this) {
        NotificationType.WEEKLY_REMINDER -> MessageTone.INFORMATIVE
        NotificationType.WEEKEND_EXPLORE -> MessageTone.INFORMATIVE
        NotificationType.HOLIDAY_EXPLORE -> MessageTone.SUGGESTIVE
    }

package com.neki.notification.domain.model

/**
 * 메시지 톤. 선언 순서가 유효: 톤 배정이 MessageTone.entries[floorMod(userId, 3)]로 인덱싱한다.
 */
enum class MessageTone {
    INFORMATIVE,
    FRIENDLY,
    SUGGESTIVE,
}

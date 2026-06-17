package com.neki.notification.domain.model

/**
 * 메시지 톤. 선언 순서가 유효(copy-spec §5): MessageTone.entries[floorMod(userId, 3)].
 */
enum class MessageTone {
    INFORMATIVE,
    FRIENDLY,
    SUGGESTIVE,
}

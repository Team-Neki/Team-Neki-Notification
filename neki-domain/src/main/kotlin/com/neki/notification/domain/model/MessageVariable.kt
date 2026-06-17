package com.neki.notification.domain.model

/**
 * 문구 치환 변수.
 */
enum class MessageVariable(val token: String) {
    RECENT_UPLOAD_DAY("최근 업로드 요일"),
    HOLIDAY_NAME("공휴일명"),
}

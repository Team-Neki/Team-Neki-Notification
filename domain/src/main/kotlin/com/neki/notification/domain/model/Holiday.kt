package com.neki.notification.domain.model

import java.time.LocalDate

/**
 * 공휴일/연휴 (batch-design §6 holiday).
 *
 * @param notifyOffsetDays 발송 시점 오프셋. 전날=-1, 당일=0.
 *                         발송일(notifyDate) = date + notifyOffsetDays.
 */
data class Holiday(
    val date: LocalDate,
    val name: String,
    val notifyOffsetDays: Int,
) {
    /** 이 공휴일을 알릴 실제 발송일. */
    val notifyDate: LocalDate
        get() = date.plusDays(notifyOffsetDays.toLong())
}

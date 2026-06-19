package com.neki.notification.domain.model

import java.time.LocalDate

data class Holiday(
    val date: LocalDate,
    val name: String,
    val notifyOffsetDays: Int,
) {
    val notifyDate: LocalDate
        get() = date.plusDays(notifyOffsetDays.toLong())
}

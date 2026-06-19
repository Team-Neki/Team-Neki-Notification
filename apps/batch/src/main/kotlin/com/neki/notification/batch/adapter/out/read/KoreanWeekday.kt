package com.neki.notification.batch.adapter.out.read

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * [최근 업로드 요일] 변수 포맷 (copy-spec §3 예시: "지난 토요일").
 *
 * 가정: 최근 업로드 일자의 요일에 "지난 " 접두사를 붙인다. 정확한 카피 톤은 추후 조정 가능하도록
 * 이 한 곳에 격리한다.
 */
internal object KoreanWeekday {

    private val LABEL: Map<DayOfWeek, String> = mapOf(
        DayOfWeek.MONDAY to "월요일",
        DayOfWeek.TUESDAY to "화요일",
        DayOfWeek.WEDNESDAY to "수요일",
        DayOfWeek.THURSDAY to "목요일",
        DayOfWeek.FRIDAY to "금요일",
        DayOfWeek.SATURDAY to "토요일",
        DayOfWeek.SUNDAY to "일요일",
    )

    /** 예: 2026-06-13(토) → "지난 토요일". */
    fun recentUploadLabel(date: LocalDate): String = "지난 ${LABEL.getValue(date.dayOfWeek)}"
}

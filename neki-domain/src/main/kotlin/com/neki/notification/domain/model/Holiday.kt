package com.neki.notification.domain.model

import java.time.LocalDate

/**
 * 공휴일 1건 (P5).
 *
 * @property date 공휴일 날짜 (ISO yyyy-MM-dd). 이 날짜 **당일(D-0)** 에 발송한다.
 * @property name 표시용 공휴일명(`[공휴일명]` 변수에 치환되는 값). 예: "추석", "어린이날".
 *               연휴 묶음/대체공휴일 접미사 없이 기본 공휴일명만 담는다(추후 정책 변경 가능).
 */
data class Holiday(
    val date: LocalDate,
    val name: String,
)

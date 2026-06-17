package com.neki.notification.domain.port

import com.neki.notification.domain.model.Holiday
import java.time.LocalDate

/**
 * 공휴일 달력 조회 포트.
 *
 * 도메인은 공휴일 데이터의 출처(CSV/DB/외부 API 등)를 알지 못한다. CSV 등 어댑터가
 * 이 계약을 구현한다(infra `CsvHolidayCalendar`).
 *
 * 현재 스코프:
 * - 발송 offset = **당일(D-0)** — 어댑터가 보유한 날짜를 그대로 발송일로 사용한다.
 * - **연휴 묶음 추론 없음** — 연휴/대체공휴일을 별도로 묶거나 추론하지 않는다.
 * - 발송 offset/연휴 묶음 정책은 현재 미적용이며 추후 도입할 수 있다.
 */
interface HolidayCalendar {
    /**
     * 주어진 날짜의 공휴일을 반환한다. 해당 날짜가 공휴일이 아니면 null.
     *
     * @param date 조회 대상 날짜(당일 매칭). 전날/다음날은 매칭되지 않는다.
     */
    fun findByDate(date: LocalDate): Holiday?

    /**
     * 보유한 모든 공휴일 엔트리.
     *
     * 반환 순서는 **보장하지 않는다**(구현체에 따라 다름). 날짜 순 등 특정 순서가
     * 필요하면 호출 측에서 정렬하라.
     */
    fun all(): List<Holiday>
}

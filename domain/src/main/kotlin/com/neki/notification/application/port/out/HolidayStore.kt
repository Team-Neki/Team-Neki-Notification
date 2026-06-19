package com.neki.notification.application.port.out

import com.neki.notification.domain.model.Holiday

/**
 * `holiday` 테이블 적재 포트. 원천(`HolidaySource`)에서 읽은 공휴일을 upsert한다.
 * 자연키(holiday_date)로 멱등 — 재동기화해도 중복 없이 갱신.
 */
interface HolidayStore {
    /** 주어진 공휴일들을 holiday_date 기준 upsert하고 처리 건수를 반환한다. */
    fun upsertAll(holidays: List<Holiday>): Int
}

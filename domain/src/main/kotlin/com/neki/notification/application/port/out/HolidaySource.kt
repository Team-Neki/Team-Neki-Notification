package com.neki.notification.application.port.out

import com.neki.notification.domain.model.Holiday

/**
 * 공휴일 데이터의 외부 원천(seed source) 포트.
 *
 * 현재 구현은 CSV(`CsvHolidaySource`)이고, 추후 Google Sheet 어댑터로 교체 예정(issue #17).
 * 동기화 Job은 이 포트로 읽어 `HolidayStore`로 `holiday` 테이블에 upsert한다.
 * (도메인 판정용 조회는 별도 포트 `HolidayCalendar` 책임 — 원천 적재와 분리)
 */
fun interface HolidaySource {
    fun load(): List<Holiday>
}

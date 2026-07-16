package com.neki.notification.batch.application

import com.neki.notification.application.port.out.HolidaySource
import com.neki.notification.application.port.out.HolidayStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 공휴일 원천(`HolidaySource`) → 인메모리 저장소(`HolidayStore`, InMemoryHolidayRepository) 적재 유스케이스.
 * 원천이 CSV든 Google Sheet(issue #17)든, 저장소가 DB든 인메모리든 이 서비스는 그대로 — 어댑터만 교체.
 */
@Component
class HolidaySyncService(
    private val source: HolidaySource,
    private val store: HolidayStore,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sync(): Int {
        val holidays = source.load()
        val count = store.upsertAll(holidays)
        log.info("공휴일 동기화 완료: {}건 upsert", count)
        return count
    }
}

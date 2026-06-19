package com.neki.notification.batch.application

import com.neki.notification.application.port.out.HolidaySource
import com.neki.notification.application.port.out.HolidayStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 공휴일 원천(`HolidaySource`) → `holiday` 테이블 동기화 유스케이스.
 * 원천이 CSV든 Google Sheet(issue #17)든 이 서비스는 그대로 — 어댑터만 교체.
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

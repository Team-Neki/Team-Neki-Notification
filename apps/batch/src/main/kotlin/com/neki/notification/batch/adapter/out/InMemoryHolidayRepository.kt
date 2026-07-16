package com.neki.notification.batch.adapter.out

import com.neki.notification.application.port.out.HolidayCalendar
import com.neki.notification.application.port.out.HolidayStore
import com.neki.notification.domain.model.Holiday
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import kotlin.math.abs

/**
 * 공휴일 인메모리 저장소. 원천(CSV 등)에서 적재한 공휴일을 프로세스 메모리에 들고 발송일 판정을
 * 그 스냅샷에서 수행한다. 공휴일은 소량·저빈도 변경이라 DB 테이블 대신 인메모리로 충분하다.
 *
 * 쓰기([HolidayStore.upsertAll])는 [com.neki.notification.batch.adapter.`in`.HolidayLoader]가 기동
 * 이벤트에 1회 적재하고, 읽기([HolidayCalendar.holidayToNotifyOn])는 배치 Job이 매일 조회한다.
 * @Volatile 불변 스냅샷을 통째로 교체(copy-on-write)해 적재(단일 이벤트 스레드)와 조회(배치 스레드)
 * 사이의 가시성을 보장한다.
 */
@Component
class InMemoryHolidayRepository : HolidayCalendar, HolidayStore {

    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var byDate: Map<LocalDate, Holiday> = emptyMap()

    /** holiday_date 기준 멱등 병합(같은 날짜는 최신 값으로 덮어씀). 새 스냅샷을 원자적으로 교체. */
    override fun upsertAll(holidays: List<Holiday>): Int {
        byDate = byDate + holidays.associateBy { it.date }
        return holidays.size
    }

    override fun holidayToNotifyOn(businessDate: LocalDate): Holiday? {
        val snapshot = byDate.values
        snapshot
            .filter { abs(it.notifyOffsetDays.toLong()) > MAX_OFFSET_DAYS }
            .forEach {
                log.warn(
                    "공휴일 '{}'의 notifyOffsetDays={}가 허용 오프셋(±{}일)을 초과합니다. 시드 데이터 점검이 필요합니다.",
                    it.name,
                    it.notifyOffsetDays,
                    MAX_OFFSET_DAYS,
                )
            }
        return snapshot.firstOrNull { it.notifyDate == businessDate }
    }

    /** 인메모리 스냅샷을 비운다(주로 테스트 격리용). */
    fun clear() {
        byDate = emptyMap()
    }

    private companion object {
        // 데이터 위생 경고 기준(전날 -1 / 당일 0 + 여유). DB 시절 SCAN_WINDOW와 달리 조회 자체를
        // 제한하지 않고(인메모리는 전수 스캔), 초과 시 WARN만 남긴다.
        const val MAX_OFFSET_DAYS = 2L
    }
}

package com.neki.notification.batch.adapter.out

import com.neki.notification.application.port.out.HolidayStore
import com.neki.notification.domain.model.Holiday
import com.neki.notification.infra.jooq.Tables.HOLIDAY
import org.jooq.DSLContext
import org.springframework.stereotype.Component

/**
 * `HolidayStore`의 jOOQ 구현. holiday_date(unique) 기준 upsert로 멱등 적재한다.
 */
@Component
class HolidayStoreAdapter(
    private val dsl: DSLContext,
) : HolidayStore {

    override fun upsertAll(holidays: List<Holiday>): Int =
        holidays.sumOf { holiday ->
            dsl.insertInto(HOLIDAY)
                .set(HOLIDAY.HOLIDAY_DATE, holiday.date)
                .set(HOLIDAY.NAME, holiday.name)
                .set(HOLIDAY.NOTIFY_OFFSET_DAYS, holiday.notifyOffsetDays)
                .onConflict(HOLIDAY.HOLIDAY_DATE)
                .doUpdate()
                .set(HOLIDAY.NAME, holiday.name)
                .set(HOLIDAY.NOTIFY_OFFSET_DAYS, holiday.notifyOffsetDays)
                .execute()
        }
}

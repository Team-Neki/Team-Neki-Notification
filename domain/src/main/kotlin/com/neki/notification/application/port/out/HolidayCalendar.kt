package com.neki.notification.application.port.out

import com.neki.notification.domain.model.Holiday
import java.time.LocalDate

/**
 * 공휴일 발송일 판정 포트 (batch-design §P5). infra가 `holiday` 테이블 조회로 구현한다.
 */
interface HolidayCalendar {
    /**
     * [businessDate]가 어떤 공휴일의 발송일(notifyDate)이면 그 공휴일을, 아니면 null을 반환한다.
     * HOLIDAY_EXPLORE Job은 매일 깨어나 이 판정으로 발송 여부와 `[공휴일명]`을 결정한다.
     */
    fun holidayToNotifyOn(businessDate: LocalDate): Holiday?
}

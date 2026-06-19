package com.neki.notification.application.port.out

import com.neki.notification.domain.model.Holiday
import java.time.LocalDate

interface HolidayCalendar {
    fun holidayToNotifyOn(businessDate: LocalDate): Holiday?
}

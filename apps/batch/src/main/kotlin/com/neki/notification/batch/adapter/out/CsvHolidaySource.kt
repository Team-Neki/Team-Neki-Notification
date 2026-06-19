package com.neki.notification.batch.adapter.out

import com.neki.notification.application.port.out.HolidaySource
import com.neki.notification.domain.model.Holiday
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * 클래스패스 CSV에서 공휴일을 읽는 `HolidaySource` 구현(issue #17의 Google Sheet 전 단계).
 *
 * 형식: `holiday_date,name,notify_offset_days` (ISO yyyy-MM-dd, UTF-8).
 * 주석(`#`)·빈 줄·헤더 행 허용. offset 컬럼 생략 시 0(당일 발송).
 */
@Component
class CsvHolidaySource(
    @Value("\${neki.batch.holiday-csv:holidays.csv}") private val resourcePath: String,
) : HolidaySource {

    override fun load(): List<Holiday> =
        ClassPathResource(resourcePath).inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .filterNot { it.startsWith(HEADER_PREFIX) }
                .map { parse(it) }
                .toList()
        }

    private fun parse(line: String): Holiday {
        val cols = line.split(",").map { it.trim() }
        return Holiday(
            date = LocalDate.parse(cols[0]),
            name = cols[1],
            notifyOffsetDays = cols.getOrNull(2)?.takeIf { it.isNotEmpty() }?.toInt() ?: 0,
        )
    }

    private companion object {
        const val HEADER_PREFIX = "holiday_date"
    }
}

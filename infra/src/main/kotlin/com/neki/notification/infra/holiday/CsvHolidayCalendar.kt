package com.neki.notification.infra.holiday

import com.neki.notification.domain.model.Holiday
import com.neki.notification.domain.port.HolidayCalendar
import java.io.InputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * CSV 기반 [HolidayCalendar] 어댑터 (P5).
 *
 * 설계 메모:
 * - **순수 Kotlin** — Spring 어노테이션/컨텍스트에 의존하지 않는다(빈 등록은 별도 config에서).
 * - 테스트 용이성을 위해 CSV 내용을 **주입 가능한 생성자**([CsvHolidayCalendar])로 받는다.
 *   엣지 케이스 CSV는 리소스 파일을 남발하지 않고 in-memory `lines`/`InputStream` 으로 주입한다.
 * - 발송 offset = 당일(D-0), 연휴 묶음 미적용 — CSV에 적힌 날짜를 그대로 사용한다.
 *
 * CSV 형식: `holidayDate,name` (헤더 1행 + 데이터, ISO yyyy-MM-dd, UTF-8).
 * 주석(`#`)/빈 줄은 무시한다. 잘못된 형식/중복 날짜는 **로드 시점에 fail-fast** 한다.
 * 단순 `split(",")` 파서이므로 **name에 쉼표를 포함할 수 없다**(RFC 4180 따옴표 이스케이프 미지원).
 * 한글 공휴일명은 쉼표가 없어 안전하다.
 *
 * @param lines 헤더를 포함한 CSV 라인들.
 */
class CsvHolidayCalendar(
    lines: List<String>,
) : HolidayCalendar {

    private val byDate: Map<LocalDate, Holiday> = parse(lines)

    override fun findByDate(date: LocalDate): Holiday? = byDate[date]

    override fun all(): List<Holiday> = byDate.values.toList()

    companion object {
        const val DEFAULT_CLASSPATH: String = "/holidays.csv"

        private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        /**
         * 원본 라인 리스트를 1-based 인덱스로 순회하며 파싱·검증한다.
         * 첫 번째 비-주석·비-빈 줄은 헤더로 간주하여 스킵한다.
         */
        private fun parse(lines: List<String>): Map<LocalDate, Holiday> {
            val result = LinkedHashMap<LocalDate, Holiday>()
            var headerSeen = false

            lines.forEachIndexed { index, raw ->
                val lineNumber = index + 1
                val trimmed = raw.trim()

                if (trimmed.isEmpty()) return@forEachIndexed
                if (trimmed.startsWith("#")) return@forEachIndexed

                if (!headerSeen) {
                    headerSeen = true
                    return@forEachIndexed
                }

                val fields = raw.split(",")
                if (fields.size < 2) {
                    fail(lineNumber, "invalid column count (missing column, expected 2)", raw)
                }
                if (fields.size > 2) {
                    fail(lineNumber, "invalid column count (too many columns, expected 2)", raw)
                }

                val dateField = fields[0].trim()
                val nameField = fields[1].trim()

                if (dateField.isEmpty()) {
                    fail(lineNumber, "blank date", raw)
                }
                if (nameField.isEmpty()) {
                    fail(lineNumber, "blank name", raw)
                }

                val date = try {
                    LocalDate.parse(dateField, ISO_DATE)
                } catch (e: DateTimeParseException) {
                    fail(lineNumber, "invalid date format (expected ISO yyyy-MM-dd)", raw)
                }

                if (result.containsKey(date)) {
                    fail(lineNumber, "duplicate date", raw)
                }

                result[date] = Holiday(date, nameField)
            }

            return result
        }

        private fun fail(lineNumber: Int, reason: String, raw: String): Nothing =
            throw IllegalArgumentException("holidays.csv line $lineNumber: $reason -> $raw")

        /**
         * [InputStream]에서 UTF-8로 CSV를 읽어 어댑터를 만든다. 스트림은 소비 후 닫는다.
         */
        @JvmStatic
        fun fromStream(input: InputStream): CsvHolidayCalendar =
            input.bufferedReader(Charsets.UTF_8).use { reader ->
                CsvHolidayCalendar(reader.readLines())
            }

        /**
         * 클래스패스 리소스에서 번들된 공휴일 CSV를 로드한다.
         *
         * @param path 클래스패스 경로. 기본값 [DEFAULT_CLASSPATH] (`/holidays.csv`).
         */
        @JvmStatic
        fun fromClasspath(path: String = DEFAULT_CLASSPATH): CsvHolidayCalendar {
            val stream = CsvHolidayCalendar::class.java.getResourceAsStream(path)
                ?: throw IllegalArgumentException("holidays.csv resource not found on classpath: $path")
            return fromStream(stream)
        }
    }
}

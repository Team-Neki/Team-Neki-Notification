package com.neki.notification.infra.holiday

import com.neki.notification.domain.model.Holiday
import org.junit.jupiter.api.Nested
import java.io.ByteArrayInputStream
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [CsvHolidayCalendar] 단위 테스트.
 *
 * 발송 offset = 당일(D-0), 연휴 추론 없음. 엣지 케이스 CSV는 `lines` 생성자로 in-memory 주입한다.
 * 로드 검증 실패 메시지 형식: "holidays.csv line {N}: {reason} -> {원문}".
 */
class CsvHolidayCalendarTest {

    private val header = "holidayDate,name"

    // ---------------------------------------------------------------------------------------
    // AC1 / AC2 — lookup hit & same-day boundary
    // ---------------------------------------------------------------------------------------

    @Nested
    inner class Lookup {

        private fun calendar() = CsvHolidayCalendar(
            listOf(
                header,
                "2026-07-17,제헌절",
            ),
        )

        @Test
        fun `AC1 hit returns Holiday with date and UTF-8 name`() {
            val result = calendar().findByDate(LocalDate.of(2026, 7, 17))

            assertNotNull(result, "expected a holiday on 2026-07-17")
            assertEquals(LocalDate.of(2026, 7, 17), result.date)
            assertEquals("제헌절", result.name)
            assertEquals(Holiday(LocalDate.of(2026, 7, 17), "제헌절"), result)
        }

        @Test
        fun `AC2 day before a holiday is null`() {
            assertNull(calendar().findByDate(LocalDate.of(2026, 7, 16)))
        }

        @Test
        fun `AC2 day after a holiday is null`() {
            assertNull(calendar().findByDate(LocalDate.of(2026, 7, 18)))
        }
    }

    // ---------------------------------------------------------------------------------------
    // AC3 / AC4 — header, comments and blank lines are not data
    // ---------------------------------------------------------------------------------------

    @Nested
    inner class Parsing {

        @Test
        fun `AC3 header row is not loaded as a holiday`() {
            val calendar = CsvHolidayCalendar(
                listOf(
                    header,
                    "2026-12-25,성탄절",
                ),
            )

            // Only the single data row is loaded; the header is not an entry.
            assertEquals(1, calendar.all().size)
            // The literal header tokens must not be parseable / present as a holiday.
            assertNull(calendar.all().firstOrNull { it.name == "name" })
        }

        @Test
        fun `AC4 comment and blank lines are ignored`() {
            val calendar = CsvHolidayCalendar(
                listOf(
                    "# leading comment",
                    "",
                    header,
                    "   ",
                    "# 2026 block",
                    "2026-12-25,성탄절",
                    "",
                    "2027-01-01,신정",
                    "# trailing comment",
                ),
            )

            assertEquals(2, calendar.all().size)
            assertEquals("성탄절", calendar.findByDate(LocalDate.of(2026, 12, 25))?.name)
            assertEquals("신정", calendar.findByDate(LocalDate.of(2027, 1, 1))?.name)
        }
    }

    // ---------------------------------------------------------------------------------------
    // AC5 / AC6 / AC8 — load-time fail-fast validation
    // ---------------------------------------------------------------------------------------

    @Nested
    inner class LoadValidation {

        // 매니저 확정 에러 메시지 SSOT:
        //   holidays.csv line {N}: {reason} -> {원문 라인}
        // 따라서 라인 번호 단언은 반드시 "line ${N}" (접두어 포함 토큰)으로 한다.
        // 맨숫자 contains("2") 같은 위양성(동어반복) 단언은 금지.
        // 각 테스트는 서로 다른 N(4~9)을 쓰고, 문제 행을 헤더/주석/빈 줄/정상 행 뒤에 배치해
        // N이 1-based 원본 라인 번호임이 토큰 단위로만 충족되게 한다.

        @Test
        fun `AC5 unparseable date fails on load with offending line`() {
            // 문제 행을 원본 line 6 에 배치 (header=1, comment=2, blank=3, valid=4, blank=5, offending=6).
            // line 토큰이 6에서만 맞도록 다른 잡음 행을 앞에 채워, 1-based 카운트(헤더/주석/빈 줄 포함)를 증명한다.
            val offending = "not-a-date,제헌절"
            val ex = assertFailsWith<IllegalArgumentException> {
                CsvHolidayCalendar(
                    listOf(
                        header,            // line 1
                        "# 2026",          // line 2
                        "",                // line 3
                        "2026-07-17,제헌절", // line 4
                        "",                // line 5
                        offending,         // line 6  <- offending
                    ),
                )
            }
            assertTrue(
                ex.message?.contains(offending) == true,
                "exception message should include the offending raw line verbatim, was: ${ex.message}",
            )
            assertTrue(
                ex.message?.contains("line 6") == true,
                "exception message should carry the 1-based line token 'line 6', was: ${ex.message}",
            )
        }

        @Test
        fun `AC5 wrong column count fails on load with offending line`() {
            // 문제 행을 원본 line 5 에 배치 (header=1, comment=2, blank=3, valid=4, offending=5).
            val offending = "2026-07-17,제헌절,extra-column"
            val ex = assertFailsWith<IllegalArgumentException> {
                CsvHolidayCalendar(
                    listOf(
                        header,            // line 1
                        "# too many cols", // line 2
                        "",                // line 3
                        "2026-08-15,광복절", // line 4
                        offending,         // line 5  <- offending (3 columns)
                    ),
                )
            }
            assertTrue(
                ex.message?.contains(offending) == true,
                "exception message should include the offending line verbatim, was: ${ex.message}",
            )
            assertTrue(
                ex.message?.contains("line 5") == true,
                "exception message should carry the 1-based line token 'line 5', was: ${ex.message}",
            )
        }

        @Test
        fun `missing column fails on load with line info`() {
            // 정책4: 쉼표가 없어 필드가 1개뿐인 행은 컬럼 부족으로 로드 시 실패.
            // 문제 행을 원본 line 4 에 배치 (header=1, comment=2, blank=3, offending=4).
            val offending = "2026-07-17"
            val ex = assertFailsWith<IllegalArgumentException> {
                CsvHolidayCalendar(
                    listOf(
                        header,     // line 1
                        "# block",  // line 2
                        "",         // line 3
                        offending,  // line 4  <- comma 없음 = 컬럼 부족
                    ),
                )
            }
            assertTrue(
                ex.message?.contains(offending) == true,
                "exception message should include the offending raw line verbatim, was: ${ex.message}",
            )
            assertTrue(
                ex.message?.contains("line 4") == true,
                "exception message should carry the 1-based line token 'line 4', was: ${ex.message}",
            )
        }

        @Test
        fun `empty name fails on load with line info`() {
            // 정책3: trim 후 name이 빈 문자열이면 로드 실패 (빈 [공휴일명] 발송 방지).
            // 문제 행을 원본 line 7 에 배치 (header=1, comment=2, blank=3, valid=4, comment=5, blank=6, offending=7).
            // line 토큰이 7에서만 맞도록 잡음 행을 풍부히 배치 — 맨숫자 contains("2") 위양성을 구조적으로 제거.
            val offending = "2026-07-17,"
            val ex = assertFailsWith<IllegalArgumentException> {
                CsvHolidayCalendar(
                    listOf(
                        header,            // line 1
                        "# names",         // line 2
                        "",                // line 3
                        "2026-08-15,광복절", // line 4
                        "# next is empty", // line 5
                        "",                // line 6
                        offending,         // line 7  <- name 비어 있음
                    ),
                )
            }
            assertTrue(
                ex.message?.contains(offending) == true,
                "exception message should include the offending raw line verbatim, was: ${ex.message}",
            )
            assertTrue(
                ex.message?.contains("line 7") == true,
                "exception message should carry the 1-based line token 'line 7', was: ${ex.message}",
            )
        }

        @Test
        fun `blank name fails on load with line info`() {
            // 정책3: 공백만 있는 name도 trim 후 빈 문자열 -> 로드 실패.
            // 문제 행을 원본 line 8 에 배치.
            val offending = "2026-07-17,   "
            val ex = assertFailsWith<IllegalArgumentException> {
                CsvHolidayCalendar(
                    listOf(
                        header,            // line 1
                        "# block A",       // line 2
                        "",                // line 3
                        "2026-08-15,광복절", // line 4
                        "",                // line 5
                        "# block B",       // line 6
                        "2026-10-03,개천절", // line 7
                        offending,         // line 8  <- name 공백뿐
                    ),
                )
            }
            assertTrue(
                ex.message?.contains(offending) == true,
                "exception message should include the offending raw line verbatim, was: ${ex.message}",
            )
            assertTrue(
                ex.message?.contains("line 8") == true,
                "exception message should carry the 1-based line token 'line 8', was: ${ex.message}",
            )
        }

        @Test
        fun `empty date fails on load with line info`() {
            // 정책2·3 대칭: name뿐 아니라 date도 비면 로드 실패.
            // 문제 행을 원본 line 4 에 배치.
            val offending = ",제헌절"
            val ex = assertFailsWith<IllegalArgumentException> {
                CsvHolidayCalendar(
                    listOf(
                        header,        // line 1
                        "# empty date",// line 2
                        "",            // line 3
                        offending,     // line 4  <- date 비어 있음
                    ),
                )
            }
            assertTrue(
                ex.message?.contains(offending) == true,
                "exception message should include the offending raw line verbatim, was: ${ex.message}",
            )
            assertTrue(
                ex.message?.contains("line 4") == true,
                "exception message should carry the 1-based line token 'line 4', was: ${ex.message}",
            )
        }

        @Test
        fun `blank date fails on load with line info`() {
            // 정책2·3 대칭: trim 후 date가 공백뿐이면 로드 실패.
            // 문제 행을 원본 line 5 에 배치.
            val offending = "   ,제헌절"
            val ex = assertFailsWith<IllegalArgumentException> {
                CsvHolidayCalendar(
                    listOf(
                        header,        // line 1
                        "# blank date",// line 2
                        "",            // line 3
                        "2026-08-15,광복절", // line 4
                        offending,     // line 5  <- date 공백뿐
                    ),
                )
            }
            assertTrue(
                ex.message?.contains(offending) == true,
                "exception message should include the offending raw line verbatim, was: ${ex.message}",
            )
            assertTrue(
                ex.message?.contains("line 5") == true,
                "exception message should carry the 1-based line token 'line 5', was: ${ex.message}",
            )
        }

        @Test
        fun `AC8 non ISO date format fails on load`() {
            // Non-ISO (예: yyyy/MM/dd) 는 거부 — 엄격히 ISO yyyy-MM-dd 만 허용.
            // 문제 행을 원본 line 6 에 배치 (header=1, comment=2, blank=3, valid=4, comment=5, offending=6).
            val offending = "2026/07/17,제헌절"
            val ex = assertFailsWith<IllegalArgumentException> {
                CsvHolidayCalendar(
                    listOf(
                        header,             // line 1
                        "# iso only",       // line 2
                        "",                 // line 3
                        "2026-07-17,제헌절",  // line 4
                        "# non-ISO below",  // line 5
                        offending,          // line 6  <- non-ISO
                    ),
                )
            }
            assertTrue(
                ex.message?.contains(offending) == true,
                "exception message should include the offending raw line verbatim, was: ${ex.message}",
            )
            assertTrue(
                ex.message?.contains("line 6") == true,
                "exception message should carry the 1-based line token 'line 6', was: ${ex.message}",
            )
        }

        @Test
        fun `AC6 duplicate holidayDate fails fast on load`() {
            // 중복 행을 원본 line 9 에 배치 (앞에 잡음·정상 행을 충분히 둬 line 토큰이 9에서만 맞도록).
            val offending = "2026-09-24,추석"
            val ex = assertFailsWith<IllegalArgumentException> {
                CsvHolidayCalendar(
                    listOf(
                        header,            // line 1
                        "# 2026 chuseok",  // line 2
                        "",                // line 3
                        "2026-08-15,광복절", // line 4
                        "",                // line 5
                        "2026-09-24,추석",  // line 6  <- first occurrence
                        "# dup below",     // line 7
                        "",                // line 8
                        offending,         // line 9  <- duplicate date
                    ),
                )
            }
            assertTrue(
                ex.message?.contains(offending) == true,
                "exception message should include the duplicated raw line verbatim, was: ${ex.message}",
            )
            assertTrue(
                ex.message?.contains("line 9") == true,
                "exception message should carry the 1-based line token 'line 9' of the duplicate, was: ${ex.message}",
            )
        }
    }

    // ---------------------------------------------------------------------------------------
    // AC9 — all() returns every entry
    // ---------------------------------------------------------------------------------------

    @Nested
    inner class AllEntries {

        @Test
        fun `AC9 all returns every parsed entry`() {
            val calendar = CsvHolidayCalendar(
                listOf(
                    header,
                    "2026-09-24,추석",
                    "2026-09-25,추석",
                    "2026-09-26,추석",
                ),
            )

            val all = calendar.all()
            assertEquals(3, all.size)
            assertEquals(
                setOf(
                    LocalDate.of(2026, 9, 24),
                    LocalDate.of(2026, 9, 25),
                    LocalDate.of(2026, 9, 26),
                ),
                all.map { it.date }.toSet(),
            )
        }
    }

    // ---------------------------------------------------------------------------------------
    // AC7 — bundled classpath resource wiring sanity
    // ---------------------------------------------------------------------------------------

    @Nested
    inner class ClasspathResource {

        @Test
        fun `AC7 bundled holidays csv loads and known dates resolve`() {
            val calendar = CsvHolidayCalendar.fromClasspath()

            assertTrue(calendar.all().isNotEmpty(), "bundled holidays.csv should load entries")
            assertEquals("추석", calendar.findByDate(LocalDate.of(2026, 9, 25))?.name)
            assertEquals("어린이날", calendar.findByDate(LocalDate.of(2027, 5, 5))?.name)
        }
    }

    // ---------------------------------------------------------------------------------------
    // 정책1 — 빈 입력/헤더만 있는 CSV는 예외가 아니라 유효한 빈 달력
    // ---------------------------------------------------------------------------------------

    @Nested
    inner class EmptyInput {

        @Test
        fun `empty lines yields an empty but valid calendar`() {
            val calendar = CsvHolidayCalendar(emptyList())

            assertTrue(calendar.all().isEmpty(), "empty input should produce no holidays")
            assertNull(
                calendar.findByDate(LocalDate.of(2026, 7, 17)),
                "lookup on an empty calendar should be null, not throw",
            )
        }

        @Test
        fun `header only yields an empty but valid calendar`() {
            val calendar = CsvHolidayCalendar(listOf(header))

            assertTrue(calendar.all().isEmpty(), "header-only input should produce no holidays")
            assertNull(
                calendar.findByDate(LocalDate.of(2026, 7, 17)),
                "lookup on a header-only calendar should be null, not throw",
            )
        }
    }

    // ---------------------------------------------------------------------------------------
    // 정책2 — 필드 trim (date/name 앞뒤 공백 제거 후 파싱/검증)
    // ---------------------------------------------------------------------------------------

    @Nested
    inner class Trimming {

        @Test
        fun `surrounding whitespace on date and name is trimmed`() {
            val calendar = CsvHolidayCalendar(
                listOf(
                    header,
                    " 2026-07-17 , 제헌절 ",
                ),
            )

            val result = calendar.findByDate(LocalDate.of(2026, 7, 17))
            assertNotNull(result, "trimmed date should still parse and resolve")
            assertEquals(
                Holiday(LocalDate.of(2026, 7, 17), "제헌절"),
                result,
                "name must be trimmed (no surrounding whitespace)",
            )
            assertEquals("제헌절", result.name, "name must not retain leading/trailing spaces")
        }
    }

    // ---------------------------------------------------------------------------------------
    // fromStream — UTF-8 스트림 직접 로드 (한글명 보존 + 조회 적중)
    // ---------------------------------------------------------------------------------------

    @Nested
    inner class StreamSource {

        @Test
        fun `fromStream reads UTF-8 lines preserving Korean names`() {
            val csv = listOf(header, "2026-09-25,추석").joinToString("\n")
            val calendar = CsvHolidayCalendar.fromStream(
                ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)),
            )

            val result = calendar.findByDate(LocalDate.of(2026, 9, 25))
            assertNotNull(result, "expected the holiday parsed from the stream")
            assertEquals("추석", result.name, "UTF-8 Korean name must be preserved through the stream")
        }
    }
}

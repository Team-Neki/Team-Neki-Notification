package com.neki.notification.infra.persistence

import com.neki.notification.domain.model.NotificationType
import com.neki.notification.domain.model.SendTarget
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.SimpleDriverDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Driver
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P3a RED — WEEKEND_EXPLORE reader 인수 테스트.
 *
 * 하네스: Testcontainers PostgreSQL(postgres:16-alpine) 1개를 클래스 전역으로 띄우고,
 * 풀 Spring 컨텍스트 없이 DataSource→NamedParameterJdbcTemplate를 직접 구성한다.
 * 매 테스트 전에 schema-shared.sql을 재적용(드롭→생성)하여 케이스 간 격리한다.
 *
 * 현 단계는 RED: [JdbcSendTargetReader.read]가 TODO()이므로
 * 모든 단언 도달 전에 NotImplementedError로 실패해야 한다(컴파일은 성공).
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcSendTargetReaderWeekendExploreTest {

    private val businessDate: LocalDate = LocalDate.of(2026, 6, 13) // 토요일

    private lateinit var jdbc: NamedParameterJdbcTemplate
    private lateinit var reader: JdbcSendTargetReader

    @BeforeAll
    fun setUpDataSource() {
        @Suppress("UNCHECKED_CAST")
        val driverClass = Class.forName("org.postgresql.Driver") as Class<out Driver>
        val ds = SimpleDriverDataSource(
            driverClass.getDeclaredConstructor().newInstance(),
            postgres.jdbcUrl,
            postgres.username,
            postgres.password,
        )
        jdbc = NamedParameterJdbcTemplate(ds)
        reader = JdbcSendTargetReader(jdbc)
    }

    @BeforeEach
    fun resetSchema() {
        jdbc.jdbcTemplate.execute(
            """
            DROP TABLE IF EXISTS TB_USER_TERM_AGREEMENT CASCADE;
            DROP TABLE IF EXISTS TB_PHOTO_IMAGE CASCADE;
            DROP TABLE IF EXISTS TB_TERM CASCADE;
            DROP TABLE IF EXISTS TB_USERS CASCADE;
            """.trimIndent(),
        )
        val ddl = ClassPathResource("reader-test-fixture.sql").inputStream.bufferedReader().use { it.readText() }
        jdbc.jdbcTemplate.execute(ddl)
    }

    // ---- 시드 헬퍼 ------------------------------------------------------------

    private val now: LocalDateTime = LocalDateTime.of(2026, 6, 1, 0, 0)

    /** 유저 1명 insert 후 생성된 id 반환. */
    private fun insertUser(name: String = "user"): Long {
        return jdbc.queryForObject(
            """
            INSERT INTO TB_USERS (email, provider_type, role, created_at, updated_at)
            VALUES (:email, 'LOCAL', 'ROLE_USER', :now, :now)
            RETURNING id
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("email", "$name@example.com")
                .addValue("now", now),
            Long::class.java,
        )!!
    }

    /** 약관 1건 insert 후 생성된 id 반환. */
    private fun insertTerm(termType: String, isActive: Boolean = true, version: String = "v1"): Long {
        return jdbc.queryForObject(
            """
            INSERT INTO TB_TERM (term_type, title, url, version, is_required, is_active, display_order, created_at, updated_at)
            VALUES (:type, :type, 'https://example.com', :version, true, :active, 0, :now, :now)
            RETURNING id
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("type", termType)
                .addValue("version", version)
                .addValue("active", isActive)
                .addValue("now", now),
            Long::class.java,
        )!!
    }

    /** 동의 이력 insert. withdrawnAt이 null이 아니면 철회된 동의. */
    private fun insertAgreement(userId: Long, termId: Long, version: String = "v1", withdrawnAt: LocalDateTime? = null) {
        jdbc.update(
            """
            INSERT INTO TB_USER_TERM_AGREEMENT
                (user_id, term_id, agreed_at, term_version, withdrawn_at, created_at, updated_at)
            VALUES (:userId, :termId, :now, :version, :withdrawnAt, :now, :now)
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("termId", termId)
                .addValue("version", version)
                .addValue("withdrawnAt", withdrawnAt)
                .addValue("now", now),
        )
    }

    private fun read(afterUserId: Long?, limit: Int): List<SendTarget> =
        reader.read(NotificationType.WEEKEND_EXPLORE, businessDate, afterUserId, limit)

    // ---- AC1: 전체 반환, user_id 오름차순 ------------------------------------

    @Test
    fun `AC1 - returns every user as SendTarget ordered by userId ascending`() {
        // 삽입 순서를 일부러 뒤섞어도 BIGSERIAL은 단조 증가하므로 삽입 순서가 곧 오름차순이다.
        val first = insertUser("c")
        val second = insertUser("a")
        val third = insertUser("b")
        // 가드: 하네스가 실제로 단조 증가 id를 발급하는지 확인(기대 리스트 구성의 전제).
        assertTrue(first < second && second < third)

        val result = read(afterUserId = null, limit = 100)

        val resultIds = result.map { it.userId }
        // .sorted() 우변 대신 알려진 오름차순(삽입 순서)으로 직접 비교.
        assertEquals(listOf(first, second, third), resultIds)
        // 결과 userId가 strictly increasing임을 별도 단언(인접 쌍 모두 first < second).
        assertTrue(resultIds.zipWithNext().all { (a, b) -> a < b })
    }

    // ---- AC2: 동의 투영(필터 아님) -------------------------------------------

    @Test
    fun `AC2 - active marketing consent without withdrawal projects pushConsent true`() {
        val userId = insertUser("agreed")
        val marketing = insertTerm("MARKETING")
        insertAgreement(userId, marketing, withdrawnAt = null)

        val result = read(afterUserId = null, limit = 100)

        val target = result.single { it.userId == userId }
        assertTrue(target.pushConsent)
    }

    @Test
    fun `AC2 - withdrawn marketing consent projects pushConsent false but is still included`() {
        val userId = insertUser("withdrawn")
        val marketing = insertTerm("MARKETING")
        insertAgreement(userId, marketing, withdrawnAt = now.plusDays(1))

        val result = read(afterUserId = null, limit = 100)

        val target = result.single { it.userId == userId }
        assertEquals(false, target.pushConsent)
    }

    @Test
    fun `AC2 - no marketing consent history projects pushConsent false but is still included`() {
        val userId = insertUser("none")

        val result = read(afterUserId = null, limit = 100)

        val target = result.single { it.userId == userId }
        assertEquals(false, target.pushConsent)
    }

    @Test
    fun `AC2 - only non-marketing consent projects pushConsent false but is still included`() {
        val userId = insertUser("service-only")
        val service = insertTerm("SERVICE")
        insertAgreement(userId, service, withdrawnAt = null)

        val result = read(afterUserId = null, limit = 100)

        val target = result.single { it.userId == userId }
        assertEquals(false, target.pushConsent)
    }

    @Test
    fun `AC2 - inactive marketing term consent without withdrawal projects pushConsent false but is still included`() {
        // 비활성(is_active=false) MARKETING 약관에 미철회 동의 → 무효 동의이므로 pushConsent=false,
        // 단 동의는 필터가 아니라 투영이므로 결과에는 포함된다.
        val userId = insertUser("inactive-marketing")
        val inactiveMarketing = insertTerm("MARKETING", isActive = false)
        insertAgreement(userId, inactiveMarketing, withdrawnAt = null)

        val result = read(afterUserId = null, limit = 100)

        val target = result.single { it.userId == userId }
        assertEquals(false, target.pushConsent)
        // 동의 무효라도 결과에서 제외되지 않음을 명시.
        assertEquals(1, result.size)
        assertEquals("", target.fcmToken)
        assertTrue(target.variables.isEmpty())
    }

    @Test
    fun `AC2 - mixed consent population projects pushConsent per user without cross-contamination`() {
        // 한 테스트에 5인을 함께 시드하여 user_id 상관성(다른 유저 동의에 오염되지 않음)과
        // 동의가 필터가 아니라 투영임을 동시에 검증.
        val activeMarketing = insertTerm("MARKETING", isActive = true, version = "v1")
        val inactiveMarketing = insertTerm("MARKETING", isActive = false, version = "v1")
        val service = insertTerm("SERVICE", isActive = true, version = "v1")

        // A: 활성 MARKETING 미철회 동의 → true
        val a = insertUser("a-active-agreed")
        insertAgreement(a, activeMarketing, withdrawnAt = null)
        // B: 활성 MARKETING 철회 동의 → false
        val b = insertUser("b-withdrawn")
        insertAgreement(b, activeMarketing, withdrawnAt = now.plusDays(1))
        // C: 동의 이력 없음 → false
        val c = insertUser("c-none")
        // D: 비-MARKETING(SERVICE) 미철회 동의만 → false
        val d = insertUser("d-service")
        insertAgreement(d, service, withdrawnAt = null)
        // E: 비활성 MARKETING 미철회 동의 → false(무효 동의)
        val e = insertUser("e-inactive-marketing")
        insertAgreement(e, inactiveMarketing, withdrawnAt = null)

        val result = read(afterUserId = null, limit = 100)

        // 전원 포함(동의는 필터가 아니다).
        assertEquals(5, result.size)
        assertEquals(
            mapOf(a to true, b to false, c to false, d to false, e to false),
            result.associate { it.userId to it.pushConsent },
        )
    }

    // ---- AC3: keyset 페이징 ---------------------------------------------------

    @Test
    fun `AC3 - keyset pagination traverses all users without duplicates or gaps`() {
        val ids = (1..5).map { insertUser("p$it") } // BIGSERIAL이므로 삽입 순서 == 오름차순

        val page1 = read(afterUserId = null, limit = 2)
        val page2 = read(afterUserId = page1.last().userId, limit = 2)
        val page3 = read(afterUserId = page2.last().userId, limit = 2)
        assertTrue(page3.isNotEmpty(), "page3 should hold the final user before requesting an empty page4")
        val page4 = read(afterUserId = page3.last().userId, limit = 2)

        assertEquals(ids.subList(0, 2), page1.map { it.userId })
        assertEquals(ids.subList(2, 4), page2.map { it.userId })
        assertEquals(ids.subList(4, 5), page3.map { it.userId })
        assertTrue(page4.isEmpty())

        // 구조적 보장: 전 페이지 누적 flatMap이 전체 ids와 순서까지 정확히 일치.
        val traversed = listOf(page1, page2, page3, page4).flatMap { page -> page.map { it.userId } }
        assertEquals(ids, traversed)
        // 중복/누락 직접 단언.
        assertEquals(ids.size, traversed.distinct().size)
    }

    @Test
    fun `AC3 - keyset pagination with evenly divisible count yields a full last page then an empty page`() {
        // 정확히 나누어떨어지는 시나리오: 4건 limit=2 → page2가 꽉 차고, page3는 빈 페이지.
        val ids = (1..4).map { insertUser("e$it") }

        val page1 = read(afterUserId = null, limit = 2)
        val page2 = read(afterUserId = page1.last().userId, limit = 2)
        val page3 = read(afterUserId = page2.last().userId, limit = 2)

        assertEquals(ids.subList(0, 2), page1.map { it.userId })
        assertEquals(ids.subList(2, 4), page2.map { it.userId }) // page2 꽉 참(size==2)
        assertEquals(2, page2.size)
        assertTrue(page3.isEmpty()) // 빈 페이지

        val traversed = listOf(page1, page2, page3).flatMap { page -> page.map { it.userId } }
        assertEquals(ids, traversed)
        assertEquals(ids.size, traversed.distinct().size)
    }

    @Test
    fun `AC3 - afterUserId cursor returns only ids strictly greater than the cursor`() {
        val ids = (1..4).map { insertUser("k$it") }

        val page1 = read(afterUserId = null, limit = 2)
        val page2 = read(afterUserId = page1.last().userId, limit = 2)

        // keyset 커서 의미 직접 단언: page2 첫 항목 id > page1 마지막 항목 id.
        assertTrue(page2.first().userId > page1.last().userId)
        // page2의 모든 id가 커서(page1 마지막)보다 크다.
        assertTrue(page2.all { it.userId > page1.last().userId })
        // 첫 페이지의 첫 항목은 가장 작은 id여야 한다(상관 검증).
        assertEquals(ids.first(), page1.first().userId)
    }

    @Test
    fun `AC3 - afterUserId larger than every existing id returns empty list`() {
        val ids = (1..3).map { insertUser("g$it") }

        // 데이터에 없는 큰 afterUserId → 빈 결과.
        val result = read(afterUserId = ids.last() + 1000, limit = 100)

        assertTrue(result.isEmpty())
    }

    // ---- AC4: fcmToken placeholder -------------------------------------------

    @Test
    fun `AC4 - every result has empty fcmToken placeholder and empty variables`() {
        repeat(3) { insertUser("u$it") }

        val result = read(afterUserId = null, limit = 100)

        assertTrue(result.all { it.fcmToken == "" })
        assertTrue(result.all { it.variables.isEmpty() })
    }

    // ---- AC5: 빈 DB -----------------------------------------------------------

    @Test
    fun `AC5 - empty database returns empty list`() {
        val result = read(afterUserId = null, limit = 100)

        assertTrue(result.isEmpty())
    }

    // ---- AC6: limit 준수 ------------------------------------------------------

    @Test
    fun `AC6 - returns exactly limit rows when seed exceeds limit`() {
        repeat(5) { insertUser("u$it") }

        val result = read(afterUserId = null, limit = 3)

        assertEquals(3, result.size)
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}

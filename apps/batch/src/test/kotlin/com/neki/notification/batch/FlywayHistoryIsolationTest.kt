package com.neki.notification.batch

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager

/**
 * 공유 DB Flyway 격리 검증.
 *
 * team-neki-server 와 동일한 DB 를 공유하므로, 기본 flyway_schema_history 테이블을 공유하면
 * 양쪽 V1/V2 가 같은 version 번호로 충돌(checksum mismatch)해 기동이 깨진다(실제 prod 장애).
 *
 * 이 테스트는 그 prod 조건을 재현한다:
 *  - 외부(server) 소유 테이블이 이미 존재(non-empty schema)
 *  - server 가 채운 기본 flyway_schema_history 에 충돌하는 V1 row 존재
 * 그리고 application.yml 과 동일한 설정(전용 history 테이블 + baseline-version=0)으로 마이그레이션해
 *  1) 충돌 없이 성공하고
 *  2) V1(notification_log/holiday)부터 실제로 적용되며
 *  3) server 의 기본 history 테이블을 건드리지 않음
 * 을 검증한다.
 */
@Testcontainers
class FlywayHistoryIsolationTest {

    @Test
    fun `전용 history 테이블 분리로 server 와 충돌 없이 V1 부터 적용된다`() {
        val url = postgres.jdbcUrl
        val user = postgres.username
        val pw = postgres.password

        // --- prod 조건 재현: 외부 소유 테이블(non-empty) + server 가 쓴 기본 history(충돌 V1) ---
        DriverManager.getConnection(url, user, pw).use { conn ->
            conn.createStatement().use { st ->
                st.execute("CREATE TABLE tb_external_owned (id BIGINT PRIMARY KEY)")
                st.execute(
                    """
                    CREATE TABLE flyway_schema_history (
                        installed_rank INT PRIMARY KEY,
                        version VARCHAR(50),
                        description VARCHAR(200),
                        type VARCHAR(20),
                        script VARCHAR(1000),
                        checksum INT,
                        installed_by VARCHAR(100),
                        installed_on TIMESTAMP DEFAULT now(),
                        execution_time INT,
                        success BOOLEAN
                    )
                    """.trimIndent(),
                )
                // server 의 V1 (notification 의 V1 과 version 은 같지만 checksum/스크립트가 다름)
                st.execute(
                    """
                    INSERT INTO flyway_schema_history
                        (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
                    VALUES (1, '1', 'create users table', 'SQL', 'V1__create_users_table.sql', -2043650209, 'server', 10, true)
                    """.trimIndent(),
                )
            }
        }

        // --- application.yml 과 동일한 Flyway 설정으로 마이그레이션 ---
        val flyway = Flyway.configure()
            .dataSource(url, user, pw)
            .table("flyway_schema_history_notification")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .locations("classpath:db/migration")
            .load()

        // 충돌하는 기본 history 가 있어도 예외 없이 성공해야 한다.
        flyway.migrate()

        DriverManager.getConnection(url, user, pw).use { conn ->
            conn.createStatement().use { st ->
                // 1) V1 이 skip 되지 않고 소유 테이블이 실제로 생성됨
                assertTrue(tableExists(st, "notification_log"), "V1 의 notification_log 가 생성되어야 한다")
                // holiday 는 V1 이 만들지만 V3(DROP)로 제거되어 인메모리로 이관됨 → 최종 스키마엔 없어야 한다(V3 적용 증거)
                assertFalse(tableExists(st, "holiday"), "holiday 는 V3 로 DROP 되어 최종 스키마에 없어야 한다")
                // 2) V2(Spring Batch 메타) 도 적용됨
                assertTrue(tableExists(st, "batch_job_instance"), "V2 의 Spring Batch 메타 테이블이 생성되어야 한다")
                // 3) 전용 history 테이블이 따로 만들어짐
                assertTrue(tableExists(st, "flyway_schema_history_notification"), "전용 history 테이블이 생성되어야 한다")
                // 4) server 의 기본 history 는 손대지 않음 (여전히 row 1개)
                st.executeQuery("SELECT count(*) FROM flyway_schema_history").use { rs ->
                    rs.next()
                    assertEquals(1, rs.getInt(1), "server 의 기본 flyway_schema_history 는 변경되지 않아야 한다")
                }
            }
        }
    }

    @Test
    fun `baseline-on-migrate=false 는 non-empty 공유 스키마에서 실패한다(설정 선택 근거)`() {
        val url = postgres.jdbcUrl
        val user = postgres.username
        val pw = postgres.password

        // 외부(server) 소유 테이블이 이미 존재 → 공유 스키마가 non-empty
        DriverManager.getConnection(url, user, pw).use { conn ->
            conn.createStatement().use { st ->
                st.execute("CREATE TABLE tb_external_owned (id BIGINT PRIMARY KEY)")
            }
        }

        // 전용 history 테이블은 아직 없는데 baseline 채택을 끄면(false),
        // Flyway 는 "Found non-empty schema but no schema history table" 로 던진다.
        // → 따라서 baseline-on-migrate=true + baseline-version=0 조합이어야 한다.
        val flyway = Flyway.configure()
            .dataSource(url, user, pw)
            .table("flyway_schema_history_notification")
            .baselineOnMigrate(false)
            .locations("classpath:db/migration")
            .load()

        val ex = org.junit.jupiter.api.assertThrows<Exception> { flyway.migrate() }
        assertTrue(
            ex.message?.contains("non-empty", ignoreCase = true) == true,
            "non-empty 스키마 + history 테이블 없음 → baseline 없이 실패해야 한다. 실제: ${ex.message}",
        )
    }

    private fun tableExists(st: java.sql.Statement, name: String): Boolean =
        st.executeQuery(
            "SELECT to_regclass('public.$name') IS NOT NULL",
        ).use { rs ->
            rs.next()
            rs.getBoolean(1)
        }

    // 인스턴스 필드 @Container → 테스트 메서드마다 새 컨테이너로 격리(두 테스트가 같은 스키마를 오염시키지 않도록).
    @Container
    val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
}

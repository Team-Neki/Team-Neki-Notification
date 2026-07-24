package com.neki.notification.batch

import com.neki.notification.application.port.out.PushSender
import com.neki.notification.domain.model.FcmResult
import com.neki.notification.domain.model.RenderedMessage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals

/**
 * H-3 best-effort 격리 검증(Testcontainers PostgreSQL).
 *
 * `tb_notification_hist`(백엔드 소유 외부 테이블)가 없어 hist 적재가 매 건 실패하는 상황을 재현한다.
 * hist 적재는 REQUIRES_NEW로 청크 트랜잭션과 분리되므로, 그 실패가 발송·`notification_log`를 깨지
 * 않고 Job은 COMPLETED로 끝나야 한다. 같은 청크 트랜잭션에서 try/catch만 했다면 PostgreSQL이
 * 트랜잭션을 abort시켜 `notification_log`까지 롤백(0건)되어 이 단언이 깨진다.
 */
@SpringBootTest
@Testcontainers
class NotificationHistBestEffortTest {

    @TestConfiguration
    class StubConfig {
        @Bean
        @Primary
        fun stubPushSender(): PushSender =
            object : PushSender {
                override fun send(token: String, message: RenderedMessage): FcmResult = FcmResult.SUCCESS
            }
    }

    @Autowired private lateinit var jobLauncher: JobLauncher
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired @Qualifier("weekendExploreJob") private lateinit var weekendExploreJob: Job

    private fun params(): JobParameters =
        JobParametersBuilder()
            .addString("businessDate", "2026-06-18")
            .addLong("runId", runId.incrementAndGet())
            .toJobParameters()

    @BeforeEach
    fun setUp() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS tb_notification (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL UNIQUE, device_token VARCHAR(512) NOT NULL, push_agreed BOOLEAN NOT NULL DEFAULT false)")
        jdbc.execute("CREATE TABLE IF NOT EXISTS tb_photo_image (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL, created_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP)")
        jdbc.execute(
            "CREATE TABLE IF NOT EXISTS notification_log (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL, notification_type VARCHAR(32) NOT NULL, message_tone VARCHAR(16) NOT NULL, variable_applied BOOLEAN NOT NULL, title VARCHAR(255) NOT NULL, body VARCHAR(500) NOT NULL, business_date DATE NOT NULL, fcm_result VARCHAR(16) NOT NULL, sent_at TIMESTAMP NOT NULL, CONSTRAINT uq_notification_log_user_type_date UNIQUE (user_id, notification_type, business_date))",
        )
        // 주의: tb_notification_hist는 일부러 만들지 않는다 → hist 적재가 매 건 실패하는 조건.
        jdbc.execute("DROP TABLE IF EXISTS tb_notification_hist")
        jdbc.execute("TRUNCATE tb_notification, tb_photo_image, notification_log RESTART IDENTITY")

        jdbc.execute(
            """
            INSERT INTO tb_notification(user_id, device_token, push_agreed) VALUES
                (1, 'tok-1', true), (3, 'tok-3', true), (5, 'tok-5', true)
            """.trimIndent(),
        )
    }

    @Test
    fun `hist 적재가 전건 실패해도 발송과 notification_log는 유지되고 Job은 완료된다`() {
        val exec = jobLauncher.run(weekendExploreJob, params())

        assertEquals(BatchStatus.COMPLETED, exec.status)
        assertEquals(
            listOf(1L, 3L, 5L),
            jdbc.queryForList(
                "SELECT user_id FROM notification_log WHERE notification_type = 'WEEKEND_EXPLORE' ORDER BY user_id",
                Long::class.java,
            ),
        )
    }

    companion object {
        private val runId = AtomicLong()

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}

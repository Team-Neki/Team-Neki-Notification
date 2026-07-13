package com.neki.notification.batch

import com.neki.notification.application.port.out.NotificationLogStore
import com.neki.notification.application.port.out.PushSender
import com.neki.notification.batch.adapter.out.NotificationLogStoreAdapter
import com.neki.notification.domain.model.NotificationStatus
import com.neki.notification.domain.model.NotificationLog
import com.neki.notification.domain.model.NotificationType
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
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertEquals

/**
 * B-5/M-3: CHUNK_SIZE=1의 건별 트랜잭션 격리 검증 (Testcontainers PostgreSQL).
 *
 * 한 건의 `save()` 실패가 같은 실행에서 이미 발송·적재된 다른 건을 롤백시키지 않음을 단언한다.
 * 청크>1이었다면 동일 청크의 선행 건들도 함께 롤백되어 0건이 남는다.
 */
@SpringBootTest
@Testcontainers
class NotificationWriterIsolationTest {

    @TestConfiguration
    class FaultConfig {
        @Bean
        @Primary
        fun stubPushSender(): PushSender =
            object : PushSender {
                override fun send(token: String, message: RenderedMessage): NotificationStatus = NotificationStatus.SENT
            }

        /** user=FAIL_USER_ID의 적재만 실패시키는 데코레이터(나머지는 실제 어댑터에 위임). */
        @Bean
        @Primary
        fun failingLogStore(adapter: NotificationLogStoreAdapter): NotificationLogStore =
            object : NotificationLogStore {
                override fun alreadySent(userId: Long, type: NotificationType, businessDate: LocalDate): Boolean =
                    adapter.alreadySent(userId, type, businessDate)

                override fun save(log: NotificationLog) {
                    if (log.userId == FAIL_USER_ID) error("의도된 적재 실패 user=$FAIL_USER_ID")
                    adapter.save(log)
                }

                override fun countByStatus(type: NotificationType, businessDate: LocalDate) =
                    adapter.countByStatus(type, businessDate)
            }
    }

    @Autowired private lateinit var jobLauncher: JobLauncher
    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired @Qualifier("weekendExploreJob") private lateinit var weekendExploreJob: Job

    private val businessDate = "2026-06-18"

    private fun params(): JobParameters =
        JobParametersBuilder()
            .addString("businessDate", businessDate)
            .addLong("runId", runId.incrementAndGet())
            .toJobParameters()

    @BeforeEach
    fun setUp() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS tb_notification (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL UNIQUE, device_token VARCHAR(512) NOT NULL, push_agreed BOOLEAN NOT NULL DEFAULT false)")
        jdbc.execute("CREATE TABLE IF NOT EXISTS tb_photo_image (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL, created_at TIMESTAMP NOT NULL)")
        jdbc.execute("CREATE TABLE IF NOT EXISTS holiday (id BIGSERIAL PRIMARY KEY, holiday_date DATE NOT NULL, name VARCHAR(64) NOT NULL, notify_offset_days INT NOT NULL, CONSTRAINT uq_holiday_date UNIQUE (holiday_date))")
        jdbc.execute(
            "CREATE TABLE IF NOT EXISTS notification_log (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL, notification_type VARCHAR(32) NOT NULL, message_tone VARCHAR(16) NOT NULL, variable_applied BOOLEAN NOT NULL, title VARCHAR(255) NOT NULL, body VARCHAR(500) NOT NULL, business_date DATE NOT NULL, status VARCHAR(16) NOT NULL, sent_at TIMESTAMP NOT NULL, CONSTRAINT uq_notification_log_user_type_date UNIQUE (user_id, notification_type, business_date))",
        )
        jdbc.execute("TRUNCATE tb_notification, tb_photo_image, holiday, notification_log RESTART IDENTITY")

        // keyset 순서: user 1, 3, 4, 5 (user2 미동의 제외). user4 적재에서 실패하도록 구성.
        jdbc.execute(
            """
            INSERT INTO tb_notification(user_id, device_token, push_agreed) VALUES
                (1, 'tok-1', true), (2, 'tok-2', false), (3, 'tok-3', true), (4, 'tok-4', true), (5, 'tok-5', true)
            """.trimIndent(),
        )
    }

    @Test
    fun `건별 트랜잭션 - 한 건 적재 실패가 선행 발송 건을 롤백시키지 않는다`() {
        val exec = jobLauncher.run(weekendExploreJob, params())

        // user4에서 save 실패 → 스텝 FAILED
        assertEquals(BatchStatus.FAILED, exec.status)
        // 선행 건(user1, user3)은 각자의 트랜잭션으로 이미 커밋됨 → 롤백되지 않음.
        // (청크>1이었다면 user1·3도 user4와 같은 청크에서 함께 롤백되어 0건)
        assertEquals(
            listOf(1L, 3L),
            jdbc.queryForList(
                "SELECT user_id FROM notification_log WHERE notification_type = 'WEEKEND_EXPLORE' ORDER BY user_id",
                Long::class.java,
            ),
        )
    }

    companion object {
        const val FAIL_USER_ID = 4L
        private val runId = AtomicLong()

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}

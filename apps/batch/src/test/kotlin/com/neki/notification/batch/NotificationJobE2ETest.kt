package com.neki.notification.batch

import com.neki.notification.domain.model.FcmResult
import com.neki.notification.domain.model.Holiday
import com.neki.notification.domain.model.RenderedMessage
import com.neki.notification.application.port.out.PushSender
import com.neki.notification.batch.adapter.out.InMemoryHolidayRepository
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
import kotlin.test.assertTrue

/**
 * P6 배치 Job 3종 E2E (Testcontainers PostgreSQL + 실제 Job 기동).
 *
 * 검증: 동의 필터 · 당일 중복(dedup) · 공휴일 발송일 게이팅 · 변수 치환 적재.
 * FCM은 [RecordingPushSender] 스텁(SUCCESS)으로 대체해 발송 호출과 적재 결과를 단언한다.
 */
@SpringBootTest
@Testcontainers
class NotificationJobE2ETest {

    @TestConfiguration
    class StubConfig {
        @Bean
        @Primary
        fun recordingPushSender(): RecordingPushSender = RecordingPushSender()
    }

    class RecordingPushSender : PushSender {
        val sent = mutableListOf<String>()
        override fun send(token: String, message: RenderedMessage): FcmResult {
            sent.add(token)
            return FcmResult.SUCCESS
        }
    }

    @Autowired private lateinit var jobLauncher: JobLauncher
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var recordingPushSender: RecordingPushSender
    @Autowired private lateinit var holidayRepository: InMemoryHolidayRepository

    @Autowired @Qualifier("weekendExploreJob") private lateinit var weekendExploreJob: Job
    @Autowired @Qualifier("weeklyReminderJob") private lateinit var weeklyReminderJob: Job
    @Autowired @Qualifier("holidayExploreJob") private lateinit var holidayExploreJob: Job

    private val businessDate = "2026-06-18" // 목요일

    private fun params(date: String = businessDate): JobParameters =
        JobParametersBuilder()
            .addString("businessDate", date)
            .addLong("runId", runId.incrementAndGet())
            .toJobParameters()

    @BeforeEach
    fun setUp() {
        recordingPushSender.sent.clear()
        holidayRepository.clear() // 인메모리 공휴일 격리(테스트 간 스냅샷 누수 방지)
        jdbc.execute("CREATE TABLE IF NOT EXISTS tb_notification (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL UNIQUE, device_token VARCHAR(512) NOT NULL, push_agreed BOOLEAN NOT NULL DEFAULT false)")
        // 실제 TB_PHOTO_IMAGE(Server #298)는 soft-delete(deleted_at)를 가진다. 픽스처도 동일하게 모사한다.
        jdbc.execute("CREATE TABLE IF NOT EXISTS tb_photo_image (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL, created_at TIMESTAMP NOT NULL, deleted_at TIMESTAMP)")
        // 공휴일은 인메모리(InMemoryHolidayRepository)라 holiday 테이블을 만들지 않는다.
        jdbc.execute(
            "CREATE TABLE IF NOT EXISTS notification_log (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL, notification_type VARCHAR(32) NOT NULL, message_tone VARCHAR(16) NOT NULL, variable_applied BOOLEAN NOT NULL, title VARCHAR(255) NOT NULL, body VARCHAR(500) NOT NULL, business_date DATE NOT NULL, fcm_result VARCHAR(16) NOT NULL, sent_at TIMESTAMP NOT NULL, CONSTRAINT uq_notification_log_user_type_date UNIQUE (user_id, notification_type, business_date))",
        )
        // tb_notification_hist는 백엔드(Team-Neki-Server V22) 소유 외부 테이블. 픽스처도 동일 스키마로 모사.
        jdbc.execute(
            "CREATE TABLE IF NOT EXISTS tb_notification_hist (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL, type VARCHAR(50) NOT NULL, title VARCHAR(100) NOT NULL, body VARCHAR(500) NOT NULL, link VARCHAR(512), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)",
        )
        jdbc.execute("TRUNCATE tb_notification, tb_photo_image, notification_log, tb_notification_hist RESTART IDENTITY")

        jdbc.execute(
            """
            INSERT INTO tb_notification(user_id, device_token, push_agreed) VALUES
                (1, 'tok-1', true), (2, 'tok-2', false), (3, 'tok-3', true), (4, 'tok-4', true), (5, 'tok-5', true)
            """.trimIndent(),
        )
        jdbc.execute(
            """
            INSERT INTO tb_photo_image(user_id, created_at) VALUES
                (1, '2026-06-11 10:00:00'), (1, '2026-06-17 09:00:00'),
                (2, '2026-06-11 10:00:00'),
                (4, '2026-05-29 12:00:00'),
                (5, '2026-06-11 10:00:00')
            """.trimIndent(),
        )
    }

    private fun logUserIds(type: String): List<Long> =
        jdbc.queryForList(
            "SELECT user_id FROM notification_log WHERE notification_type = ? ORDER BY user_id",
            Long::class.java,
            type,
        )

    private fun histUserIds(type: String): List<Long> =
        jdbc.queryForList(
            "SELECT user_id FROM tb_notification_hist WHERE type = ? ORDER BY user_id",
            Long::class.java,
            type,
        )

    @Test
    fun `weekend - 동의자 전원 발송·적재, 미동의 제외`() {
        val exec = jobLauncher.run(weekendExploreJob, params())

        assertEquals(BatchStatus.COMPLETED, exec.status)
        assertEquals(listOf(1L, 3L, 4L, 5L), logUserIds("WEEKEND_EXPLORE")) // user2 미동의 제외
        assertEquals(4, recordingPushSender.sent.size)
        assertEquals(
            4,
            jdbc.queryForObject("SELECT count(*) FROM notification_log WHERE fcm_result = 'SUCCESS'", Int::class.java),
        )
        // 성공 발송분은 최근알림 피드(tb_notification_hist)에도 그대로 적재된다.
        assertEquals(listOf(1L, 3L, 4L, 5L), histUserIds("WEEKEND_EXPLORE"))
    }

    @Test
    fun `weekend - 재실행해도 당일 중복 발송 안 함(dedup)`() {
        jobLauncher.run(weekendExploreJob, params())
        recordingPushSender.sent.clear()

        jobLauncher.run(weekendExploreJob, params()) // 같은 businessDate, 새 runId

        assertEquals(0, recordingPushSender.sent.size, "2회차에는 모두 ALREADY_SENT로 스킵")
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM notification_log", Int::class.java))
        // 재실행 시 발송 자체가 스킵되므로 hist도 중복 적재되지 않는다.
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM tb_notification_hist", Int::class.java))
    }

    @Test
    fun `weekly - 7일 전 업로드 동의자만, 변수 톤은 개인화 적재`() {
        val exec = jobLauncher.run(weeklyReminderJob, params())

        assertEquals(BatchStatus.COMPLETED, exec.status)
        // 06-11 업로드 + 동의: user1, user5 (user2 미동의 제외)
        assertEquals(listOf(1L, 5L), logUserIds("WEEKLY_REMINDER"))
        // user5 → floorMod(5,3)=2 → SUGGESTIVE(변수 필요) → "지난 목요일" 치환
        val title = jdbc.queryForObject(
            "SELECT title FROM notification_log WHERE user_id = 5 AND notification_type = 'WEEKLY_REMINDER'",
            String::class.java,
        )
        assertEquals("지난 목요일처럼 오늘도 남겨볼까요?", title)
        assertTrue(
            jdbc.queryForObject(
                "SELECT variable_applied FROM notification_log WHERE user_id = 5 AND notification_type = 'WEEKLY_REMINDER'",
                Boolean::class.java,
            )!!,
        )
        // hist에도 동일한 렌더 결과(type=enum명, title)가 그대로 적재된다.
        assertEquals(listOf(1L, 5L), histUserIds("WEEKLY_REMINDER"))
        assertEquals(
            "지난 목요일처럼 오늘도 남겨볼까요?",
            jdbc.queryForObject(
                "SELECT title FROM tb_notification_hist WHERE user_id = 5 AND type = 'WEEKLY_REMINDER'",
                String::class.java,
            ),
        )
    }

    @Test
    fun `weekly - soft-deleted 사진은 업로드 집계에서 제외된다`() {
        // user1의 7일 전(06-11) 업로드를 soft-delete → user1은 최근 업로드 없음으로 제외, user5만 남는다.
        jdbc.execute(
            "UPDATE tb_photo_image SET deleted_at = '2026-06-12 00:00:00' " +
                "WHERE user_id = 1 AND created_at = '2026-06-11 10:00:00'",
        )

        val exec = jobLauncher.run(weeklyReminderJob, params())

        assertEquals(BatchStatus.COMPLETED, exec.status)
        assertEquals(listOf(5L), logUserIds("WEEKLY_REMINDER"))
    }

    @Test
    fun `holiday - 발송일이 아니면 아무 것도 발송하지 않음`() {
        // holiday 테이블 비어 있음 → 2026-06-18은 발송일 아님
        val exec = jobLauncher.run(holidayExploreJob, params())

        assertEquals(BatchStatus.COMPLETED, exec.status)
        assertEquals(0, recordingPushSender.sent.size)
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notification_log", Int::class.java))
    }

    @Test
    fun `holiday - 발송일이면 최근 1달 업로드 동의자에게 공휴일명 치환 발송`() {
        holidayRepository.upsertAll(listOf(Holiday(LocalDate.of(2026, 6, 18), "테스트공휴일", 0)))

        val exec = jobLauncher.run(holidayExploreJob, params())

        assertEquals(BatchStatus.COMPLETED, exec.status)
        // 최근 1달 업로드 + 동의: user1, user4, user5
        assertEquals(listOf(1L, 4L, 5L), logUserIds("HOLIDAY_EXPLORE"))
        // user1 → floorMod(1,3)=1 → FRIENDLY(변수 필요) → "테스트공휴일에 약속 있으신가요?"
        val title = jdbc.queryForObject(
            "SELECT title FROM notification_log WHERE user_id = 1 AND notification_type = 'HOLIDAY_EXPLORE'",
            String::class.java,
        )
        assertEquals("테스트공휴일에 약속 있으신가요?", title)
    }

    companion object {
        // 전역 유일 runId: JUnit 메서드별 인스턴스 생성과 무관하게 JobInstance 충돌 방지.
        private val runId = AtomicLong()

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
    }
}

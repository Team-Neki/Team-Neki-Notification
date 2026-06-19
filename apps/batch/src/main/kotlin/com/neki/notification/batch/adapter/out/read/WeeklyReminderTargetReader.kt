package com.neki.notification.batch.adapter.out.read

import com.neki.notification.domain.model.MessageVariable
import com.neki.notification.domain.model.SendTarget
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * WEEKLY_REMINDER 발송 대상 리더 (shared-db §1: 7일 전 사진 업로드 이력 + 푸시동의).
 *
 * 대상 = `push_agreed = true` 이면서 `businessDate - 7일`에 사진 업로드 이력이 있는 유저.
 * 변수 `[최근 업로드 요일]` = 해당 유저의 가장 최근 업로드 일자의 요일 (copy-spec §3).
 */
@Component
class WeeklyReminderTargetReader(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    private val sql = """
        SELECT n.user_id,
               n.device_token,
               (SELECT MAX(p.created_at) FROM tb_photo_image p WHERE p.user_id = n.user_id) AS last_upload
        FROM tb_notification n
        WHERE n.push_agreed = true
          AND n.user_id > :after
          AND EXISTS (
              SELECT 1 FROM tb_photo_image p2
              WHERE p2.user_id = n.user_id
                AND p2.created_at >= :windowStart
                AND p2.created_at < :windowEnd
          )
        ORDER BY n.user_id
        LIMIT :limit
    """.trimIndent()

    fun readPage(businessDate: LocalDate, afterUserId: Long, pageSize: Int): List<SendTarget> {
        val uploadDay = businessDate.minusDays(7)
        val params = MapSqlParameterSource()
            .addValue("after", afterUserId)
            .addValue("limit", pageSize)
            .addValue("windowStart", uploadDay.atStartOfDay())
            .addValue("windowEnd", uploadDay.plusDays(1).atStartOfDay())
        return jdbc.query(sql, params) { rs, _ ->
            val lastUpload = rs.getTimestamp("last_upload").toLocalDateTime().toLocalDate()
            SendTarget(
                userId = rs.getLong("user_id"),
                fcmToken = rs.getString("device_token"),
                pushConsent = true,
                variables = mapOf(
                    MessageVariable.RECENT_UPLOAD_DAY to KoreanWeekday.recentUploadLabel(lastUpload),
                ),
            )
        }
    }
}

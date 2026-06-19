package com.neki.notification.batch.adapter.out.read

import com.neki.notification.domain.model.MessageVariable
import com.neki.notification.domain.model.SendTarget
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class HolidayExploreTargetReader(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    private val sql = """
        SELECT n.user_id, n.device_token
        FROM tb_notification n
        WHERE n.push_agreed = true
          AND n.user_id > :after
          AND EXISTS (
              SELECT 1 FROM tb_photo_image p
              WHERE p.user_id = n.user_id
                AND p.created_at >= :monthAgo
          )
        ORDER BY n.user_id
        LIMIT :limit
    """.trimIndent()

    fun readPage(
        businessDate: LocalDate,
        holidayName: String,
        afterUserId: Long,
        pageSize: Int,
    ): List<SendTarget> {
        val params = MapSqlParameterSource()
            .addValue("after", afterUserId)
            .addValue("limit", pageSize)
            .addValue("monthAgo", businessDate.minusMonths(1).atStartOfDay())
        return jdbc.query(sql, params) { rs, _ ->
            SendTarget(
                userId = rs.getLong("user_id"),
                fcmToken = rs.getString("device_token"),
                pushConsent = true,
                variables = mapOf(MessageVariable.HOLIDAY_NAME to holidayName),
            )
        }
    }
}

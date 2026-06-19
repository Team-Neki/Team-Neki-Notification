package com.neki.notification.batch.adapter.out.read

import com.neki.notification.domain.model.SendTarget
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

@Component
class WeekendExploreTargetReader(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    private val sql = """
        SELECT n.user_id, n.device_token
        FROM tb_notification n
        WHERE n.push_agreed = true
          AND n.user_id > :after
        ORDER BY n.user_id
        LIMIT :limit
    """.trimIndent()

    fun readPage(afterUserId: Long, pageSize: Int): List<SendTarget> {
        val params = MapSqlParameterSource()
            .addValue("after", afterUserId)
            .addValue("limit", pageSize)
        return jdbc.query(sql, params) { rs, _ ->
            SendTarget(
                userId = rs.getLong("user_id"),
                fcmToken = rs.getString("device_token"),
                pushConsent = true,
            )
        }
    }
}

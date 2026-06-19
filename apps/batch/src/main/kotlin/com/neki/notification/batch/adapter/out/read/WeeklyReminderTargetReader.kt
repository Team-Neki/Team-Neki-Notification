package com.neki.notification.batch.adapter.out.read

import com.neki.notification.domain.model.MessageVariable
import com.neki.notification.domain.model.SendTarget
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class WeeklyReminderTargetReader(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    private val sql = """
        SELECT n.user_id,
               n.device_token,
               (SELECT MAX(p.created_at) FROM tb_photo_image p WHERE p.user_id = n.user_id) AS last_upload
        FROM tb_notification n
        WHERE ${TargetReaderSupport.PAGING_PREDICATE}
          AND EXISTS (
              SELECT 1 FROM tb_photo_image p2
              WHERE p2.user_id = n.user_id
                AND p2.created_at >= :windowStart
                AND p2.created_at < :windowEnd
          )
        ${TargetReaderSupport.PAGING_TAIL}
    """.trimIndent()

    fun readPage(businessDate: LocalDate, afterUserId: Long, pageSize: Int): List<SendTarget> {
        val uploadDay = businessDate.minusDays(7)
        val params = TargetReaderSupport.pagingParams(afterUserId, pageSize)
            .addValue("windowStart", uploadDay.atStartOfDay())
            .addValue("windowEnd", uploadDay.plusDays(1).atStartOfDay())
        return jdbc.query(sql, params) { rs, _ ->
            val lastUpload = rs.getTimestamp("last_upload").toLocalDateTime().toLocalDate()
            TargetReaderSupport.sendTarget(
                rs,
                mapOf(MessageVariable.RECENT_UPLOAD_DAY to KoreanWeekday.recentUploadLabel(lastUpload)),
            )
        }
    }
}

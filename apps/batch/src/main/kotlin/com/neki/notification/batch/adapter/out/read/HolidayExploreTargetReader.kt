package com.neki.notification.batch.adapter.out.read

import com.neki.notification.domain.model.MessageVariable
import com.neki.notification.domain.model.SendTarget
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
        WHERE ${TargetReaderSupport.PAGING_PREDICATE}
          AND EXISTS (
              SELECT 1 FROM tb_photo_image p
              WHERE p.user_id = n.user_id
                AND p.created_at >= :monthAgo
          )
        ${TargetReaderSupport.PAGING_TAIL}
    """.trimIndent()

    fun readPage(
        businessDate: LocalDate,
        holidayName: String,
        afterUserId: Long,
        pageSize: Int,
    ): List<SendTarget> {
        val params = TargetReaderSupport.pagingParams(afterUserId, pageSize)
            .addValue("monthAgo", businessDate.minusMonths(1).atStartOfDay())
        return jdbc.query(sql, params) { rs, _ ->
            TargetReaderSupport.sendTarget(rs, mapOf(MessageVariable.HOLIDAY_NAME to holidayName))
        }
    }
}

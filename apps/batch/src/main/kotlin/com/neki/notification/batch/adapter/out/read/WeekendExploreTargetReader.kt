package com.neki.notification.batch.adapter.out.read

import com.neki.notification.domain.model.SendTarget
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

@Component
class WeekendExploreTargetReader(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    private val sql = """
        SELECT n.user_id, n.device_token
        FROM tb_notification n
        WHERE ${TargetReaderSupport.PAGING_PREDICATE}
        ${TargetReaderSupport.PAGING_TAIL}
    """.trimIndent()

    fun readPage(afterUserId: Long, pageSize: Int): List<SendTarget> =
        jdbc.query(sql, TargetReaderSupport.pagingParams(afterUserId, pageSize)) { rs, _ ->
            TargetReaderSupport.sendTarget(rs)
        }
}

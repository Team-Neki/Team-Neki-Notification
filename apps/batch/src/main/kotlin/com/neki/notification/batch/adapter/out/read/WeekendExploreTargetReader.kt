package com.neki.notification.batch.adapter.out.read

import com.neki.notification.domain.model.SendTarget
import org.jooq.DSLContext
import org.springframework.stereotype.Component

@Component
class WeekendExploreTargetReader(
    private val dsl: DSLContext,
) {
    private val sql = """
        SELECT n.user_id, n.device_token
        FROM tb_notification n
        WHERE ${TargetReaderSupport.PAGING_PREDICATE}
        ${TargetReaderSupport.PAGING_TAIL}
    """.trimIndent()

    fun readPage(afterUserId: Long, pageSize: Int): List<SendTarget> =
        dsl.fetch(sql, afterUserId, pageSize).map { TargetReaderSupport.sendTarget(it) }
}

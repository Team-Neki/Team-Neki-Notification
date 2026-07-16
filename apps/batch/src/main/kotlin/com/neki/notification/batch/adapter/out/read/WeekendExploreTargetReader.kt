package com.neki.notification.batch.adapter.out.read

import com.neki.notification.domain.model.SendTarget
import org.jooq.DSLContext
import org.springframework.stereotype.Component

@Component
class WeekendExploreTargetReader(
    private val dsl: DSLContext,
) {
    fun readPage(afterUserId: Long, pageSize: Int): List<SendTarget> =
        TargetReaderSupport.query(dsl, afterUserId, pageSize)
            .map { TargetReaderSupport.sendTarget(it) }
}

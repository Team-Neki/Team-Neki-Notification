package com.neki.notification.batch.adapter.out.read

import com.neki.notification.domain.model.MessageVariable
import com.neki.notification.domain.model.SendTarget
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class HolidayExploreTargetReader(
    private val dsl: DSLContext,
) {
    fun readPage(
        businessDate: LocalDate,
        holidayName: String,
        afterUserId: Long,
        pageSize: Int,
    ): List<SendTarget> {
        val monthAgo = businessDate.minusMonths(1).atStartOfDay()

        val p = DSL.table(DSL.name("tb_photo_image")).`as`("p")
        val pUserId = DSL.field(DSL.name("p", "user_id"), Long::class.java)
        val pCreatedAt = DSL.field(DSL.name("p", "created_at"), LocalDateTime::class.java)
        val pDeletedAt = DSL.field(DSL.name("p", "deleted_at"), LocalDateTime::class.java)
        val uploadedRecently = DSL.exists(
            DSL.selectOne()
                .from(p)
                .where(
                    pUserId.eq(TargetReaderSupport.USER_ID)
                        .and(pDeletedAt.isNull)
                        .and(pCreatedAt.ge(monthAgo)),
                ),
        )

        return TargetReaderSupport.query(dsl, afterUserId, pageSize, extraCondition = uploadedRecently)
            .map { TargetReaderSupport.sendTarget(it, mapOf(MessageVariable.HOLIDAY_NAME to holidayName)) }
    }
}

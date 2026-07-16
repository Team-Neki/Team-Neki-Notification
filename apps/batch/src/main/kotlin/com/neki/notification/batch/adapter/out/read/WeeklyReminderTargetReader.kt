package com.neki.notification.batch.adapter.out.read

import com.neki.notification.domain.model.MessageVariable
import com.neki.notification.domain.model.SendTarget
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class WeeklyReminderTargetReader(
    private val dsl: DSLContext,
) {
    fun readPage(businessDate: LocalDate, afterUserId: Long, pageSize: Int): List<SendTarget> {
        val uploadDay = businessDate.minusDays(7)
        val start = uploadDay.atStartOfDay()
        val end = uploadDay.plusDays(1).atStartOfDay()

        val p = DSL.table(DSL.name("tb_photo_image")).`as`("p")
        val pUserId = DSL.field(DSL.name("p", "user_id"), Long::class.java)
        val pCreatedAt = DSL.field(DSL.name("p", "created_at"), LocalDateTime::class.java)
        val pDeletedAt = DSL.field(DSL.name("p", "deleted_at"), LocalDateTime::class.java)
        val lastUpload = DSL.field(
            DSL.select(DSL.max(pCreatedAt))
                .from(p)
                .where(pUserId.eq(TargetReaderSupport.USER_ID).and(pDeletedAt.isNull)),
        ).`as`("last_upload")

        val p2 = DSL.table(DSL.name("tb_photo_image")).`as`("p2")
        val p2UserId = DSL.field(DSL.name("p2", "user_id"), Long::class.java)
        val p2CreatedAt = DSL.field(DSL.name("p2", "created_at"), LocalDateTime::class.java)
        val p2DeletedAt = DSL.field(DSL.name("p2", "deleted_at"), LocalDateTime::class.java)
        val uploadedThatDay = DSL.exists(
            DSL.selectOne()
                .from(p2)
                .where(
                    p2UserId.eq(TargetReaderSupport.USER_ID)
                        .and(p2DeletedAt.isNull)
                        .and(p2CreatedAt.ge(start))
                        .and(p2CreatedAt.lt(end)),
                ),
        )

        return TargetReaderSupport.query(dsl, afterUserId, pageSize, listOf(lastUpload), uploadedThatDay)
            .map {
                val lastUploadDay = it.get("last_upload", LocalDateTime::class.java).toLocalDate()
                TargetReaderSupport.sendTarget(
                    it,
                    mapOf(MessageVariable.RECENT_UPLOAD_DAY to KoreanWeekday.recentUploadLabel(lastUploadDay)),
                )
            }
    }
}

package com.neki.notification.batch.adapter.out.read

import com.neki.notification.domain.model.MessageVariable
import com.neki.notification.domain.model.SendTarget
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class WeeklyReminderTargetReader(
    private val dsl: DSLContext,
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
                AND p2.created_at >= ?
                AND p2.created_at < ?
          )
        ${TargetReaderSupport.PAGING_TAIL}
    """.trimIndent()

    fun readPage(businessDate: LocalDate, afterUserId: Long, pageSize: Int): List<SendTarget> {
        val uploadDay = businessDate.minusDays(7)
        return dsl.fetch(
            sql,
            afterUserId,
            uploadDay.atStartOfDay(),
            uploadDay.plusDays(1).atStartOfDay(),
            pageSize,
        ).map {
            val lastUpload = it.get("last_upload", LocalDateTime::class.java).toLocalDate()
            TargetReaderSupport.sendTarget(
                it,
                mapOf(MessageVariable.RECENT_UPLOAD_DAY to KoreanWeekday.recentUploadLabel(lastUpload)),
            )
        }
    }
}

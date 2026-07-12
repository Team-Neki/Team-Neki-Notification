package com.neki.notification.batch.adapter.out

import com.neki.notification.application.port.out.NotificationLogStore
import com.neki.notification.domain.model.FcmResult
import com.neki.notification.domain.model.NotificationLog
import com.neki.notification.domain.model.NotificationType
import com.neki.notification.infra.jooq.Tables.NOTIFICATION_LOG
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Component
class NotificationLogStoreAdapter(
    private val dsl: DSLContext,
    private val clock: Clock,
) : NotificationLogStore {

    override fun alreadySent(userId: Long, type: NotificationType, businessDate: LocalDate): Boolean =
        dsl.fetchExists(
            dsl.selectOne()
                .from(NOTIFICATION_LOG)
                .where(NOTIFICATION_LOG.USER_ID.eq(userId))
                .and(NOTIFICATION_LOG.NOTIFICATION_TYPE.eq(type.name))
                .and(NOTIFICATION_LOG.BUSINESS_DATE.eq(businessDate)),
        )

    override fun countByResult(type: NotificationType, businessDate: LocalDate): Map<FcmResult, Long> =
        dsl.select(NOTIFICATION_LOG.FCM_RESULT, DSL.count())
            .from(NOTIFICATION_LOG)
            .where(NOTIFICATION_LOG.NOTIFICATION_TYPE.eq(type.name))
            .and(NOTIFICATION_LOG.BUSINESS_DATE.eq(businessDate))
            .groupBy(NOTIFICATION_LOG.FCM_RESULT)
            .fetch()
            .associate { FcmResult.valueOf(it.value1()) to it.value2().toLong() }

    override fun save(log: NotificationLog) {
        val sentAt = (log.sentAt ?: Instant.now(clock)).atOffset(ZoneOffset.UTC)
        dsl.insertInto(NOTIFICATION_LOG)
            .set(NOTIFICATION_LOG.USER_ID, log.userId)
            .set(NOTIFICATION_LOG.NOTIFICATION_TYPE, log.notificationType.name)
            .set(NOTIFICATION_LOG.MESSAGE_TONE, log.messageTone.name)
            .set(NOTIFICATION_LOG.VARIABLE_APPLIED, log.variableApplied)
            .set(NOTIFICATION_LOG.TITLE, log.title)
            .set(NOTIFICATION_LOG.BODY, log.body)
            .set(NOTIFICATION_LOG.BUSINESS_DATE, log.businessDate)
            .set(NOTIFICATION_LOG.FCM_RESULT, log.fcmResult.name)
            .set(NOTIFICATION_LOG.SENT_AT, sentAt)
            .execute()
    }
}

package com.neki.notification.batch.adapter.out

import com.neki.notification.application.port.out.NotificationLogStore
import com.neki.notification.domain.model.NotificationLog
import com.neki.notification.domain.model.NotificationType
import com.neki.notification.infra.persistence.NotificationLogEntity
import com.neki.notification.infra.persistence.NotificationLogJpaRepository
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * [NotificationLogStore]의 JPA 구현 (batch-design §4 persistence-write).
 *
 * `sentAt`이 비어 있으면 적재 시각을 주입한다. 동일 키 동시 적재는 DB unique 제약이 최종 방어선.
 */
@Component
class NotificationLogStoreAdapter(
    private val repository: NotificationLogJpaRepository,
    private val clock: Clock = Clock.systemUTC(),
) : NotificationLogStore {

    override fun alreadySent(userId: Long, type: NotificationType, businessDate: LocalDate): Boolean =
        repository.existsByUserIdAndNotificationTypeAndBusinessDate(userId, type, businessDate)

    override fun save(log: NotificationLog) {
        repository.save(NotificationLogEntity.from(log, Instant.now(clock)))
    }
}

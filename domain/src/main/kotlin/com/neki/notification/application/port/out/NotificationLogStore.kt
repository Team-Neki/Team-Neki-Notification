package com.neki.notification.application.port.out

import com.neki.notification.domain.model.NotificationLog
import com.neki.notification.domain.model.NotificationType
import java.time.LocalDate

/**
 * 발송 이력 저장/조회 포트 (batch-design §4). infra가 JPA로 구현한다.
 *
 * 이력 조회(중복 판정의 원천)는 포트 책임이고, "이미 발송됨" 불리언을 받은 뒤의
 * 발송 제외 판정은 도메인(NotificationProcessor) 책임이다 (copy-spec §7).
 */
interface NotificationLogStore {
    /** (userId, type, businessDate) 키로 이미 발송된 이력이 있는지. */
    fun alreadySent(userId: Long, type: NotificationType, businessDate: LocalDate): Boolean

    /** 발송 이력 1건 적재. unique (user_id, notification_type, business_date) 제약으로 최종 중복 방지. */
    fun save(log: NotificationLog)
}

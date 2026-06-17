package com.neki.notification.domain.port

import com.neki.notification.domain.model.NotificationType
import java.time.LocalDate

/**
 * 발송 이력 조회 포트. 멱등성(중복 발송 방지)을 위한 dedup 조회 책임을 가진다.
 *
 * 도메인 서비스(NotificationProcessor)는 이력 조회 자체를 수행하지 않고,
 * 어댑터가 이 포트로 조회한 결과(alreadySent)를 입력으로 받는다.
 *
 * save()는 P4(발송 결과 기록)로 연기한다 — 이 단계에서는 정의하지 않는다.
 */
interface NotificationLogRepository {
    /**
     * 주어진 (유저, 타입, 영업일) 조합으로 이미 발송된 이력이 있는지 여부.
     */
    fun isAlreadySent(userId: Long, type: NotificationType, businessDate: LocalDate): Boolean
}

package com.neki.notification.application.port.out

import com.neki.notification.domain.model.FcmResult
import com.neki.notification.domain.model.NotificationLog
import com.neki.notification.domain.model.NotificationType
import java.time.LocalDate

interface NotificationLogStore {
    fun alreadySent(userId: Long, type: NotificationType, businessDate: LocalDate): Boolean

    fun save(log: NotificationLog)

    /**
     * 해당 (type, businessDate) 발송의 FcmResult 별 건수를 집계한다.
     * 발송된 각 대상은 정확히 1건의 이력을 가지므로(중복 재처리 없음), 이 집계는 그 발송의 결과 분포와 같다.
     * 결과가 0건인 FcmResult 는 맵에서 생략된다.
     */
    fun countByResult(type: NotificationType, businessDate: LocalDate): Map<FcmResult, Long>
}

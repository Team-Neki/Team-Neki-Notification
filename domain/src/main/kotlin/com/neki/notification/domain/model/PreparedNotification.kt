package com.neki.notification.domain.model

import java.time.LocalDate

/**
 * 발송 확정된 1건 (Processor → Writer 전달 DTO, batch-design §5).
 *
 * Writer가 [target]의 토큰으로 [message]를 발송한 뒤, 그 결과로 NotificationLog를 적재한다.
 */
data class PreparedNotification(
    val target: SendTarget,
    val type: NotificationType,
    val message: RenderedMessage,
    val businessDate: LocalDate,
)

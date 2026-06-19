package com.neki.notification.batch.application.step

import com.neki.notification.domain.model.NotificationType
import com.neki.notification.domain.model.PreparedNotification
import com.neki.notification.domain.model.SendDecision
import com.neki.notification.domain.model.SendTarget
import com.neki.notification.application.port.out.NotificationLogStore
import com.neki.notification.domain.service.NotificationProcessor
import org.springframework.batch.item.ItemProcessor
import java.time.LocalDate

class NotificationItemProcessor(
    private val type: NotificationType,
    private val businessDate: LocalDate,
    private val logStore: NotificationLogStore,
) : ItemProcessor<SendTarget, PreparedNotification> {

    override fun process(item: SendTarget): PreparedNotification? {
        val alreadySent = logStore.alreadySent(item.userId, type, businessDate)
        return when (val decision = NotificationProcessor.decide(item, type, alreadySent)) {
            is SendDecision.Send -> PreparedNotification(item, type, decision.message, businessDate)
            is SendDecision.Skip -> null
        }
    }
}

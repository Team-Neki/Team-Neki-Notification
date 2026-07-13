package com.neki.notification.batch.application.step

import com.neki.notification.domain.model.NotificationLog
import com.neki.notification.domain.model.PreparedNotification
import com.neki.notification.application.port.out.NotificationLogStore
import com.neki.notification.application.port.out.PushSender
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter

class NotificationItemWriter(
    private val pushSender: PushSender,
    private val logStore: NotificationLogStore,
) : ItemWriter<PreparedNotification> {

    override fun write(chunk: Chunk<out PreparedNotification>) {
        for (prepared in chunk) {
            val result = pushSender.send(prepared.target.fcmToken, prepared.message)
            logStore.save(
                NotificationLog.of(
                    target = prepared.target,
                    type = prepared.type,
                    message = prepared.message,
                    businessDate = prepared.businessDate,
                    status = result,
                ),
            )
        }
    }
}

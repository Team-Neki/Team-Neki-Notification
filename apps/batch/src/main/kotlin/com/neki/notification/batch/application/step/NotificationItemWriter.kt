package com.neki.notification.batch.application.step

import com.neki.notification.domain.model.NotificationLog
import com.neki.notification.domain.model.PreparedNotification
import com.neki.notification.application.port.out.NotificationLogStore
import com.neki.notification.application.port.out.PushSender
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter

/**
 * 발송 + 이력 적재 (batch-design §5 Composite Writer).
 *
 * 각 건을 FCM 발송하고 그 결과(SUCCESS/FAILED/SKIPPED)로 NotificationLog를 적재한다.
 * 동일 키 동시 적재는 DB unique 제약이 최종 방어선이다.
 */
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
                    fcmResult = result,
                ),
            )
        }
    }
}

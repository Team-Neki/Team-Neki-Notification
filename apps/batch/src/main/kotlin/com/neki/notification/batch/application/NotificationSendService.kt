package com.neki.notification.batch.application

import com.neki.notification.application.port.out.NotificationLogStore
import com.neki.notification.application.port.out.PushSender
import com.neki.notification.domain.model.NotificationLog
import com.neki.notification.domain.model.NotificationType
import com.neki.notification.domain.model.PreparedNotification
import com.neki.notification.domain.model.SendDecision
import com.neki.notification.domain.model.SendTarget
import com.neki.notification.domain.service.NotificationProcessor
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * 알림 1건 처리 유스케이스(batch-design §5). "여러 건을 어떤 단위로 읽고 커밋하는가"(페이징·청크·
 * 트랜잭션 경계)는 배치 어댑터(adapter/in/batch)의 책임이고, 이 서비스는 프레임워크와 무관하게
 * "1건을 어떻게 준비/발송하는가"만 담당한다. 그래서 배치 외 트리거(예: 어드민 단건 발송)에서도
 * 재사용 가능하다.
 *
 * 발송은 반드시 [prepare] → [dispatch] 순서. [dispatch]의 트랜잭션 경계(건별 커밋, B-5/M-3)는
 * 호출자인 청크 스텝(CHUNK_SIZE=1)이 소유한다.
 */
@Component
class NotificationSendService(
    private val logStore: NotificationLogStore,
    private val pushSender: PushSender,
) {
    /**
     * 발송 대상 → 발송 확정 변환. 당일 중복 여부를 조회한 뒤 [NotificationProcessor]로 판정한다.
     * Skip(미동의/중복)이면 null.
     */
    fun prepare(target: SendTarget, type: NotificationType, businessDate: LocalDate): PreparedNotification? {
        val alreadySent = logStore.alreadySent(target.userId, type, businessDate)
        return when (val decision = NotificationProcessor.decide(target, type, alreadySent)) {
            is SendDecision.Send -> PreparedNotification(target, type, decision.message, businessDate)
            is SendDecision.Skip -> null
        }
    }

    /**
     * 준비된 1건을 FCM 발송하고 그 결과(SUCCESS/FAILED/SKIPPED)로 이력을 적재한다.
     * send-then-save는 외부 부수효과 후 트랜잭션 적재라 본질적으로 dual-write이며, 롤백 시 중복
     * 발송 위험은 호출자의 CHUNK_SIZE=1(건별 커밋)로 1건으로 한정된다(B-5/M-3).
     */
    fun dispatch(prepared: PreparedNotification) {
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

package com.neki.notification.batch.application

import com.neki.notification.application.port.out.NotificationHistStore
import com.neki.notification.application.port.out.NotificationLogStore
import com.neki.notification.application.port.out.PushSender
import com.neki.notification.domain.model.FcmResult
import com.neki.notification.domain.model.NotificationLog
import com.neki.notification.domain.model.NotificationType
import com.neki.notification.domain.model.PreparedNotification
import com.neki.notification.domain.model.SendDecision
import com.neki.notification.domain.model.SendTarget
import com.neki.notification.domain.service.NotificationProcessor
import org.slf4j.LoggerFactory
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
    private val histStore: NotificationHistStore,
) {
    private val log = LoggerFactory.getLogger(javaClass)

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
     *
     * 발송 성공(SUCCESS) 건은 추가로 `tb_notification_hist`(앱 최근알림 피드)에도 기록한다(H-3).
     * `notification_log`는 우리 소유·중복 방지의 단일 출처라 전 결과를 적재하지만, hist는 유저가
     * 실제로 받은 알림만 보여야 하므로 SUCCESS만 남긴다(백엔드 `SendPushUseCase`와 동일). hist 적재는
     * best-effort — 실패해도 발송·`notification_log`는 유지하고 경고만 남긴다([recordHist]).
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
        if (result == FcmResult.SUCCESS) {
            recordHist(prepared)
        }
    }

    /**
     * 발송 성공 건을 `tb_notification_hist`에 best-effort로 적재한다. 이 테이블은 백엔드 소유
     * 외부 테이블이라 스키마 드리프트 등으로 적재가 실패할 수 있는데, 그 실패가 핵심 파이프라인
     * (발송·`notification_log`)을 깨선 안 된다. 커넥션 격리는 어댑터의 REQUIRES_NEW가, 예외 흡수는
     * 여기 try/catch가 담당한다(H-3). link(딥링크)는 현재 없어 null.
     */
    private fun recordHist(prepared: PreparedNotification) {
        try {
            histStore.save(
                userId = prepared.target.userId,
                type = prepared.type,
                title = prepared.message.title,
                body = prepared.message.body,
                link = null,
            )
        } catch (e: Exception) {
            log.warn(
                "tb_notification_hist 적재 실패(best-effort, 발송·notification_log는 유지) userId={} type={}",
                prepared.target.userId,
                prepared.type,
                e,
            )
        }
    }
}

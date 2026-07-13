package com.neki.notification.batch.application.step

import com.neki.notification.application.port.out.NotificationLogStore
import com.neki.notification.domain.model.NotificationStatus
import com.neki.notification.domain.model.NotificationType
import org.slf4j.LoggerFactory
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.StepExecutionListener
import java.time.LocalDate

/**
 * 스텝(=잡) 종료 시 FCM 발송 결과 요약을 로그로 남긴다.
 *
 * 집계 단위가 **잡(스텝 실행)** 인 이유: CHUNK_SIZE=1(건별 트랜잭션)이라 청크 단위 집계는 매번 1건이 되어 무의미하다.
 * 발송된 각 대상은 정확히 1건의 notification_log 를 가지므로(중복 재처리 없음), (type, businessDate) 집계가 곧 이 발송의 결과 분포다.
 * 커밋된 이력을 조회하므로 writer 의 인메모리 상태에 의존하지 않는다.
 */
class SendResultSummaryListener(
    private val type: NotificationType,
    private val logStore: NotificationLogStore,
) : StepExecutionListener {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun afterStep(stepExecution: StepExecution): ExitStatus? {
        val businessDate = stepExecution.jobParameters.getString("businessDate")
            ?.let(LocalDate::parse)
            ?: return stepExecution.exitStatus

        val counts = logStore.countByStatus(type, businessDate)
        val sent = counts[NotificationStatus.SENT] ?: 0L
        val failed = counts[NotificationStatus.FAILED] ?: 0L
        val dead = counts[NotificationStatus.DEAD] ?: 0L
        val skipped = counts[NotificationStatus.SKIPPED] ?: 0L
        val total = sent + failed + dead + skipped

        // 운영 관측/테스트를 위해 스텝 실행 컨텍스트에도 기록한다(Batch 메타에 남음).
        stepExecution.executionContext.apply {
            putLong("notif.total", total)
            putLong("notif.sent", sent)
            putLong("notif.failed", failed)
            putLong("notif.dead", dead)
            putLong("notif.skipped", skipped)
        }

        log.info(
            "[{}] 발송 요약 businessDate={}: 총 {}건 (SENT={}, FAILED={}, DEAD={}, SKIPPED={})",
            type, businessDate, total, sent, failed, dead, skipped,
        )
        return stepExecution.exitStatus
    }
}

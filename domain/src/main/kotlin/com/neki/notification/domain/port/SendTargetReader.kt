package com.neki.notification.domain.port

import com.neki.notification.domain.model.NotificationType
import com.neki.notification.domain.model.SendTarget
import java.time.LocalDate

/**
 * 발송 대상 조회 포트 (배치 Reader가 의존).
 *
 * 동의 필터 책임(중요): Reader는 푸시 수신 동의 여부로 **필터링하지 않고**,
 * [SendTarget.pushConsent]에 원시값을 그대로 담아 반환한다. 동의 최종 재확인은
 * NotificationProcessor의 책임이다(copy-spec §6, 설계 §5). 쿼리에서 미동의 유저를
 * 제외하면 NotificationProcessor의 NOT_CONSENTED 분기가 배치에서 실행되지 않는다.
 *
 * 잠정 계약: afterUserId 기반 keyset 페이징을 가정한다.
 * 구체 keyset 페이징(정렬 키/커서 인코딩 등)은 P3에서 확정한다.
 */
interface SendTargetReader {
    /**
     * @param afterUserId 직전 페이지 마지막 userId. 첫 페이지면 null.
     * @param limit 페이지 크기.
     */
    fun read(
        type: NotificationType,
        businessDate: LocalDate,
        afterUserId: Long?,
        limit: Int,
    ): List<SendTarget>
}

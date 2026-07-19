package com.neki.notification.application.port.out

import com.neki.notification.domain.model.NotificationType

/**
 * 발송에 성공한 알림을 백엔드 소유 `tb_notification_hist`에 기록하는 포트.
 *
 * 앱의 "최근 알림" 피드(Team-Neki-Server `GetRecentNotificationsUseCase`)는 이 테이블을 읽는다.
 * 배치 발송분도 피드에 노출하려면 발송 후 여기 적재해야 한다. 이 테이블은 백엔드(외부) 소유라
 * 우리 스키마 관리 대상이 아니고, 적재는 best-effort다(실패해도 발송/`NotificationLogStore`는 유지).
 */
interface NotificationHistStore {
    /**
     * 발송된 알림 1건을 이력으로 적재한다. `type`은 [NotificationType.name] 문자열로 저장한다.
     * `link`(딥링크)는 현재 배치 알림에 없어 호출부가 null을 넘기며, 도입 시 채운다.
     *
     * 기본 파라미터 값을 두지 않는다: 인터페이스에 default 값을 두면 Kotlin이 비-인터페이스 합성
     * 클래스(`$DefaultImpls`)를 만들어 아웃바운드 포트=인터페이스 ArchUnit 규칙을 깨기 때문.
     */
    fun save(userId: Long, type: NotificationType, title: String, body: String, link: String?)
}

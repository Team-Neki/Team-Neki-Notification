package com.neki.notification.batch.adapter.`in`

import com.neki.notification.batch.application.HolidaySyncService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 애플리케이션 기동 완료 시 공휴일 원천(CSV)을 인메모리 저장소로 적재하는 인바운드 트리거.
 *
 * `neki.batch.holiday-sync-enabled=true`일 때만 활성(운영 한정, 스케줄러 게이팅과 동일 결).
 * CSV는 배포 아티팩트라 "기동 시 적재"로 충분하다. 외부 원천(Google Sheet, issue #17)으로 가면
 * 재배포 없이 바뀌므로 재적재가 필요해 `@Scheduled` cron 트리거로 전환한다.
 */
@Component
@ConditionalOnProperty(prefix = "neki.batch", name = ["holiday-sync-enabled"], havingValue = "true")
class HolidayLoader(
    private val holidaySyncService: HolidaySyncService,
) {
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        holidaySyncService.sync()
    }
}

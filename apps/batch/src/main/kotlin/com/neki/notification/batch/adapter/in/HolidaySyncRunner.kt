package com.neki.notification.batch.adapter.`in`

import com.neki.notification.batch.application.HolidaySyncService
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 기동 시 공휴일 동기화를 1회 수행하는 인바운드 트리거.
 *
 * `neki.batch.holiday-sync-enabled=true`일 때만 활성(운영 한정, 스케줄러 게이팅과 동일 결).
 * CSV는 배포 아티팩트에 포함되므로 "배포 시 시드"로 충분하다. 외부 원천(Google Sheet,
 * issue #17)으로 가면 재배포 없이 바뀌므로 `@Scheduled` cron 트리거로 전환한다.
 */
@Component
@ConditionalOnProperty(prefix = "neki.batch", name = ["holiday-sync-enabled"], havingValue = "true")
class HolidaySyncRunner(
    private val holidaySyncService: HolidaySyncService,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments?) {
        holidaySyncService.sync()
    }
}

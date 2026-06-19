package com.neki.notification.infra.scheduling

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Clock
import java.time.ZoneId

/**
 * 스케줄링 기술 인프라 (도메인 무관, 재사용 가능 모듈).
 *
 * `@EnableScheduling` 으로 `@Scheduled` 인프라를 켜고, 운영 타임존 기준 [Clock] 을 제공한다.
 * "무엇을 언제 돌릴지"(cron 표현식·활성화 토글)는 이 모듈의 책임이 아니라 각 앱의 adapter/in 정책이다.
 * 그래서 앱 전용 속성(neki.batch.*)에 의존하지 않고 일반 속성 `scheduling.zone` 만 사용한다.
 */
@Configuration
@EnableScheduling
class SchedulingConfiguration {
    /** 운영 타임존 기준 시계. 기본 Asia/Seoul, `scheduling.zone` 으로 재정의 가능. */
    @Bean
    fun clock(@Value("\${scheduling.zone:Asia/Seoul}") zone: String): Clock =
        Clock.system(ZoneId.of(zone))
}

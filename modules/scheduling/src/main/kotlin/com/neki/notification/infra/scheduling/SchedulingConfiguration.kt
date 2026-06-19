package com.neki.notification.infra.scheduling

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Clock
import java.time.ZoneId

@Configuration
@EnableScheduling
class SchedulingConfiguration {
    @Bean
    fun clock(@Value("\${scheduling.zone:Asia/Seoul}") zone: String): Clock =
        Clock.system(ZoneId.of(zone))
}

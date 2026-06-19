package com.neki.notification.batch.config

import org.jooq.conf.RenderNameCase
import org.jooq.conf.RenderQuotedNames
import org.springframework.boot.autoconfigure.jooq.DefaultConfigurationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * jOOQ 렌더링 설정. codegen이 DDLDatabase로 식별자를 대문자로 생성하므로,
 * 런타임에는 PostgreSQL의 소문자 폴딩 규칙에 맞춰 **소문자·무인용**으로 렌더한다.
 * (그대로 두면 "NOTIFICATION_LOG"."USER_ID" 인용 대문자로 렌더되어 실제 소문자 컬럼과 어긋남)
 */
@Configuration
class JooqConfig {

    @Bean
    fun jooqConfigurationCustomizer(): DefaultConfigurationCustomizer =
        DefaultConfigurationCustomizer { configuration ->
            configuration.set(
                configuration.settings()
                    .withRenderNameCase(RenderNameCase.LOWER)
                    .withRenderQuotedNames(RenderQuotedNames.NEVER)
                    .withRenderSchema(false),
            )
        }
}

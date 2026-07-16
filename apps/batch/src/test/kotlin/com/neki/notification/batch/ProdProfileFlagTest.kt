package com.neki.notification.batch

import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import org.springframework.core.env.PropertySource
import kotlin.test.assertEquals

/**
 * prod 프로파일(`application-prod.yml`)이 켜는 기능 플래그 고정
 * (`docs/lld/scheduling-and-config.md §4` 표와 동일해야 한다).
 *
 * 플래그는 `@ConditionalOnProperty`로 기동 시 1회만 평가되는 빈 게이팅이라, 값이 조용히
 * 바뀌면 운영 동작이 통째로 달라진다(실발송 on/off, 인증 없는 테스트 API 노출 여부).
 * 컨텍스트 기동은 datasource ENC + JASYPT_PASSWORD가 필요하므로, 여기서는 프로파일
 * YAML 자체를 로드해 값만 단언한다.
 */
class ProdProfileFlagTest {

    private val prodProperties: PropertySource<*> =
        YamlPropertySourceLoader()
            .load("application-prod", ClassPathResource("application-prod.yml"))
            .single()

    private fun flag(name: String): Any? = prodProperties.getProperty(name)

    @Test
    fun `prod 는 실제 FCM 발송을 켠다`() {
        assertEquals(true, flag("neki.fcm.enabled"))
    }

    @Test
    fun `prod 는 스케줄러와 공휴일 적재를 켠다`() {
        assertEquals(true, flag("neki.batch.scheduling-enabled"))
        assertEquals(true, flag("neki.batch.holiday-sync-enabled"))
    }

    /**
     * 런북(docs/runbook/deployment.md §5)의 배포 검증 스모크가 이 엔드포인트를 전제한다.
     * 주의: 이 API 는 인증이 없고 prod 는 실발송이다. 이 단언을 `true` 로 유지하는 한
     * 8080 을 클러스터 밖으로 노출해서는 안 된다.
     */
    @Test
    fun `prod 는 수동 트리거 테스트 API 를 켠다 - pod 내부 전용 전제`() {
        assertEquals(true, flag("neki.test-api.enabled"))
    }
}

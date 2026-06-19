package com.neki.notification.infra.persistence.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * postgresql 영속성 모듈의 JPA 구성 (config는 module 소유).
 *
 * 엔티티/리포지토리 스캔 범위를 이 모듈 패키지로 한정해 모듈을 자기완결적으로 만든다.
 * 포트를 구현하는 어댑터는 apps 모듈의 adapter.out 으로 분리되어 있고,
 * 여기서는 영속성 기술 구성(엔티티·Spring Data JPA 리포지토리 등록)만 책임진다.
 */
@Configuration
@EntityScan(basePackages = ["com.neki.notification.infra.persistence"])
@EnableJpaRepositories(basePackages = ["com.neki.notification.infra.persistence"])
class PostgresPersistenceConfig

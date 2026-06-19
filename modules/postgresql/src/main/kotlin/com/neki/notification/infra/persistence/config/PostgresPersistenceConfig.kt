package com.neki.notification.infra.persistence.config

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EntityScan(basePackages = ["com.neki.notification.infra.persistence"])
@EnableJpaRepositories(basePackages = ["com.neki.notification.infra.persistence"])
class PostgresPersistenceConfig

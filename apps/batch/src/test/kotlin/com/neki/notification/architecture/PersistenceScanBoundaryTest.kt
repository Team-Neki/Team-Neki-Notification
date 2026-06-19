package com.neki.notification.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import jakarta.persistence.Entity
import org.junit.jupiter.api.Test
import org.springframework.data.repository.Repository

/**
 * 컴포넌트 스캔 이중화 가드 (B-3/M-1, Docker 불필요).
 *
 * 루트 `@SpringBootApplication`은 `com.neki.notification` 전역을 스캔하지만,
 * `PostgresPersistenceConfig`의 `@EntityScan`/`@EnableJpaRepositories`는
 * `com.neki.notification.infra.persistence`로 한정한다. 두 선언은 현재 멱등이라
 * 무해하나, JPA 엔티티/리포지토리가 이 패키지 밖으로 이동하면 모듈 config가
 * 조용히 스캔에서 누락한다. 이 테스트가 "JPA 엔티티·리포는 infra.persistence
 * 하위에 둔다"는 제약을 고정해 그 드리프트를 컴파일/CI 시점에 막는다.
 */
class PersistenceScanBoundaryTest {

    private val importedClasses = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.neki.notification")

    @Test
    fun `JPA 엔티티는 infra_persistence 패키지 하위에 둔다`() {
        classes()
            .that().areAnnotatedWith(Entity::class.java)
            .should().resideInAPackage(PERSISTENCE_PACKAGE)
            .because(
                "PostgresPersistenceConfig의 @EntityScan이 $PERSISTENCE_PACKAGE 로 한정되어 있어, " +
                    "이 밖의 엔티티는 컴포넌트 스캔에서 조용히 누락된다",
            )
            .check(importedClasses)
    }

    @Test
    fun `Spring Data 리포지토리는 infra_persistence 패키지 하위에 둔다`() {
        classes()
            .that().areAssignableTo(Repository::class.java)
            .should().resideInAPackage(PERSISTENCE_PACKAGE)
            .because(
                "PostgresPersistenceConfig의 @EnableJpaRepositories가 $PERSISTENCE_PACKAGE 로 한정되어 있어, " +
                    "이 밖의 리포지토리는 빈으로 등록되지 않는다",
            )
            .check(importedClasses)
    }

    private companion object {
        const val PERSISTENCE_PACKAGE = "com.neki.notification.infra.persistence.."
    }
}

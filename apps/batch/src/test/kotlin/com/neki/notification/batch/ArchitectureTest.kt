package com.neki.notification.batch

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/**
 * 헥사고날/클린 아키텍처 계층 의존성 규칙(ArchUnit). 프로덕션 코드만 분석(DoNotIncludeTests).
 *
 * 핵심은 [springBatchOnlyInAdapterIn] — Spring Batch(구동 메커니즘)가 application 계층으로 새는 것을
 * 구조적으로 막는다(과거 NotificationJobLauncher가 application/launch에 있던 위반의 재발 방지).
 */
@AnalyzeClasses(
    packages = ["com.neki.notification"],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class ArchitectureTest {

    @ArchTest
    val domainIsFrameworkFree: ArchRule =
        noClasses()
            .that().resideInAnyPackage(DOMAIN, PORT)
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "org.jooq..")
            .because("도메인/포트(core)는 프레임워크(Spring·jOOQ)에 의존하지 않는다")

    @ArchTest
    val domainDoesNotDependOnApp: ArchRule =
        noClasses()
            .that().resideInAnyPackage(DOMAIN, PORT)
            .should().dependOnClassesThat().resideInAPackage("com.neki.notification.batch..")
            .because("core(도메인/포트)는 바깥(배치 앱)에 의존하지 않는다 — 의존은 안쪽으로만")

    @ArchTest
    val applicationIsFrameworkAndAdapterFree: ArchRule =
        noClasses()
            .that().resideInAPackage(APP)
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework.batch..",
                "org.springframework.web..",
                ADAPTER,
            )
            .because("application 유스케이스는 Spring Batch·web·adapter에 의존하지 않는다(포트만 의존)")

    @ArchTest
    val springBatchOnlyInAdapterIn: ArchRule =
        noClasses()
            .that().resideOutsideOfPackage(ADAPTER_IN)
            .should().dependOnClassesThat().resideInAPackage("org.springframework.batch..")
            .because("Spring Batch(구동 메커니즘)는 인바운드 어댑터(adapter/in)에서만 사용한다")

    @ArchTest
    val outboundPortsAreInterfaces: ArchRule =
        classes()
            .that().resideInAPackage(PORT)
            .should().beInterfaces()
            .because("아웃바운드 포트는 인터페이스여야 한다(어댑터가 구현)")

    private companion object {
        const val DOMAIN = "com.neki.notification.domain.."
        const val PORT = "com.neki.notification.application.port.."
        const val APP = "com.neki.notification.batch.application.."
        const val ADAPTER = "com.neki.notification.batch.adapter.."
        const val ADAPTER_IN = "com.neki.notification.batch.adapter.in.."
    }
}

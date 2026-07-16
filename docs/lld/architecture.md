# LLD — 아키텍처 / 모듈 구조

**무엇을 만드는가**는 `docs/prd/`, **파일별 근거 주석**은 [code-notes.md](code-notes.md) 참조. 본 문서는 모듈 경계·의존 규칙·기술 스택을 다룬다.

## 1. Gradle 멀티모듈

`settings.gradle.kts`:

```
apps:batch/          부트 jar — Batch Job/Step 조립, @Scheduled, in/out 어댑터, 설정, Flyway, jOOQ codegen
   └─ depends on → domain, modules:fcm, modules:scheduling
domain/              순수 Kotlin — 모델·정책·도메인서비스 + application 포트(out). 프레임워크 의존성 0 (불변식)
modules:fcm/         Firebase Admin SDK 초기화 설정 (FirebaseConfig)
modules:scheduling/  @EnableScheduling + 운영 타임존 Clock (SchedulingConfiguration)
```

> 루트의 `neki-application` / `neki-domain` / `neki-infra` 디렉토리는 settings.gradle.kts `include`에 없는 **잔재**다. 빌드 대상은 위 4개 모듈뿐.

배포 산출물은 `apps:batch`의 bootJar **하나**뿐이다(`apps/batch/build.gradle.kts`에서 plain jar 비활성).

## 2. 헥사고날(포트&어댑터) 경계

- `domain`은 **포트(out) 인터페이스만** 정의한다: `application/port/out/{PushSender, NotificationLogStore, HolidayCalendar, HolidayStore, HolidaySource, SendTargetReader}`.
- `apps:batch`의 어댑터가 구현을 채운다:
  - `adapter/in` — 인바운드: `NotificationJobScheduler`(cron), `NotificationJobLauncher`(잡 기동 공용), `HolidayLoader`(기동 완료 이벤트 1회), `TestNotificationController`(수동 트리거, `neki.test-api.enabled` 게이트), `batch/`(Spring Batch 조립·스텝).
  - `adapter/out` — 아웃바운드: `NotificationLogStoreAdapter`(jOOQ)·`InMemoryHolidayRepository`(인메모리)·`read/*TargetReader`(jOOQ DSL)·`fcm/{FcmPushSender, LoggingPushSender}`.
- `apps:batch`의 `application/`은 프레임워크 무관 유스케이스만 담당하고, Spring Batch 조립·구동은 `adapter/in/batch`에 둔다(ArchUnit `springBatchOnlyInAdapterIn`이 강제).

**의존 규칙(불변식)**: `domain`은 어떤 프레임워크에도 의존하지 않는다. 도메인 순수성을 깨는 변경은 금지.

## 3. 레이어별 책임

| 레이어 | 위치 | 책임 |
| --- | --- | --- |
| 도메인 모델 | `domain/model` | `NotificationType`, `MessageTone`(선언 순서 유효), `SendTarget`, `RenderedMessage`, `NotificationLog`, `Holiday`, `SendDecision`, `FcmResult` 등 |
| 도메인 정책 | `domain/policy` | `MessageRenderer`(문구 렌더·폴백), `ToneAssignmentPolicy`(톤 배정) — 순수 함수 |
| 도메인 서비스 | `domain/service` | `NotificationProcessor.decide()` — 중복 판정 + 렌더 조립 (순수) |
| 포트(out) | `domain/application/port/out` | 인프라가 구현할 인터페이스 |
| 유스케이스 | `apps:batch/application` | `NotificationSendService`(1건 준비/발송), `HolidaySyncService`(공휴일 적재) — 프레임워크 무관 |
| 어댑터 | `apps:batch/adapter` | in: cron/launcher/web + `batch/`(Spring Batch 조립·스텝) · out: jOOQ / 인메모리 / FCM 구현 |

## 4. 기술 스택

| 영역 | 선택 | 버전 (`gradle/libs.versions.toml`) |
| --- | --- | --- |
| 언어 / 런타임 | Kotlin / JDK | 2.0.21 / 21 |
| 프레임워크 | Spring Boot + Spring Batch | 3.4.5 |
| 데이터 접근 | jOOQ (+ starter-jdbc, DataSourceTransactionManager) | 3.19.22 (Boot BOM 런타임과 일치) |
| codegen | nu.studer.jooq 플러그인 (DDLDatabase, 라이브 DB 불필요) | 9.0 |
| 마이그레이션 | Flyway (앱 소유 테이블 V1·V2) | Boot BOM |
| 웹/헬스 | starter-web(테스트 트리거 API) + starter-actuator | Boot BOM |
| 푸시 | Firebase Admin SDK | 9.4.3 |
| 테스트 | JUnit5, MockK, Testcontainers(PostgreSQL), Kover | 1.13.13 / 1.20.6 / 0.9.1 |

데이터 접근을 jOOQ+Flyway 한 스택으로 통일하고 JPA·JdbcTemplate·QueryDSL을 배제한 결정과 근거: [ADR 0001](../adr/0001-jooq-over-jpa.md).

## 5. 관련 문서

- 배치 파이프라인/멱등성: [batch-pipeline.md](batch-pipeline.md)
- 데이터 접근(jOOQ/Flyway/외부 스키마): [data-access.md](data-access.md)
- 스케줄링/설정/기능 플래그: [scheduling-and-config.md](scheduling-and-config.md)
- 공휴일 동기화: [holiday-sync.md](holiday-sync.md)
- 파일별 근거 주석 인덱스: [code-notes.md](code-notes.md)
- 역사적 설계 기록: `docs/superpowers/specs/` (일부 결정은 ADR 0001로 대체됨 — 예: "쓰기 JPA"는 폐기)

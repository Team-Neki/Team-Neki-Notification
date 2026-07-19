# LLD — 데이터 접근 (jOOQ + Flyway)

결정·근거는 [ADR 0001](../adr/0001-jooq-over-jpa.md). 본 문서는 현행 구현 세부를 다룬다.

## 1. 소유권 경계 = 관리 경계

| 구분 | 테이블 | 관리 |
| --- | --- | --- |
| **앱 소유** | `notification_log` | Flyway V1 마이그레이션 + jOOQ **codegen 대상**(생성 타입으로 타입세이프 쿼리) |
| **Spring Batch 메타** | `BATCH_*` | Flyway V2 마이그레이션 |
| **외부 소유(read-only)** | `tb_notification`, `tb_photo_image` | 스키마 관리·codegen **안 함**. jOOQ **DSL**(이름 기반 `DSL.table`/`DSL.field`)로 조회 |
| **외부 소유(write)** | `tb_notification_hist` | 백엔드(Team-Neki-Server V22) 소유. 스키마 관리·codegen **안 함**. jOOQ **DSL** 이름 기반 INSERT. 발송 성공분을 best-effort로 적재(앱 "최근 알림" 피드용) → §6·[batch-pipeline.md §1](batch-pipeline.md). |

> 공휴일은 V1이 만든 `holiday` 테이블을 V3(`V3__drop_holiday_table.sql`)로 제거하고 인메모리(`InMemoryHolidayRepository`)로 이관했다. codegen은 V1만 읽으므로 `excludes="HOLIDAY"`로 생성 타입에서 뺀다. 상세는 [holiday-sync.md](holiday-sync.md).

- 스키마 SSOT: `apps/batch/src/main/resources/db/migration/V1__notification_schema.sql`, `V2__spring_batch_schema.sql`.
- codegen: 라이브 DB 없이 Flyway V1 DDL에서 `DDLDatabase`로 생성 → `com.neki.notification.infra.jooq`(`build/` 하위, 미추적).
- 소유↔외부 모두 단일 `DSLContext` API로 통일(JdbcTemplate 혼용 제거).

## 2. 소유 테이블 스키마

### `notification_log`
`id, user_id, notification_type, message_tone, variable_applied, title, body, business_date, fcm_result, sent_at`
- `UNIQUE(user_id, notification_type, business_date)` — 중복 발송 최종 방어선.
- `INDEX(business_date)`.

(공휴일은 인메모리로 이관되어 소유 테이블이 없다 — [holiday-sync.md](holiday-sync.md) 참조.)

## 3. 어댑터

| 어댑터 | 포트 | 방식 | 비고 |
| --- | --- | --- | --- |
| `NotificationLogStoreAdapter` | `NotificationLogStore` | jOOQ 생성 타입 | `insertInto`/`fetchExists`. `sentAt` 없으면 적재 시각 주입(UTC). `clock` 빈 주입 강제. |
| `NotificationHistStoreAdapter` | `NotificationHistStore` | jOOQ DSL(이름 기반) | 외부 소유 `tb_notification_hist`에 `insertInto`. `type`=enum명, `id`·`created_at`·`updated_at`은 DB DEFAULT 위임. **REQUIRES_NEW**로 청크 트랜잭션과 분리(best-effort 격리, H-3). |
| `InMemoryHolidayRepository` | `HolidayCalendar`+`HolidayStore` | 인메모리 | `@Volatile` 스냅샷, `holiday_date` 멱등 병합 → [holiday-sync.md](holiday-sync.md) |
| `WeeklyReminderTargetReader` | (Reader) | jOOQ DSL | 7일 전 업로드 EXISTS + `[최근 업로드 요일]`, soft-delete 제외 |
| `WeekendExploreTargetReader` | (Reader) | jOOQ DSL | 동의자 전원 |
| `HolidayExploreTargetReader` | (Reader) | jOOQ DSL | 최근 1달 업로드 EXISTS + `[공휴일명]`, soft-delete 제외 |

공통 골격은 `TargetReaderSupport.query(dsl, after, size, extraColumns, extraCondition)`:
- 푸시 동의(`push_agreed=true`)·keyset 커서(`user_id > ?`)·정렬·LIMIT를 **강제** → 각 리더는 고유 SELECT 컬럼·WHERE 조건만 얹는다. **동의 필터의 단일 출처**를 구조적으로 보장(리더가 못 빠뜨림).
- 외부 테이블은 codegen 대상이 아니라 jOOQ 이름 기반 참조(`DSL.table`/`DSL.field`). `sendTarget(record, variables)`가 `Record`→`SendTarget` 매핑.

## 4. 렌더 케이스 (`JooqConfig`) — 함정 주의

codegen(`DDLDatabase`)이 식별자를 **대문자**로 생성한다. PostgreSQL은 인용 없는 식별자를 **소문자로 폴딩**하므로, 런타임에 그대로 렌더하면 `"NOTIFICATION_LOG"."USER_ID"`(인용 대문자)로 나가 실제 소문자 컬럼과 어긋난다.

→ `RenderNameCase.LOWER` + `RenderQuotedNames.NEVER`(+`renderSchema=false`)로 렌더. `Settings` 빈이 아니라 Spring Boot의 `DefaultConfigurationCustomizer`로 적용(전자는 자동구성에 안 먹힘). 외부 테이블 리더도 jOOQ DSL로 조립하므로(이름 기반 참조) 동일 렌더 설정을 탄다.

## 5. Flyway 공유 DB history 분리

공유 DB에서 `team-neki-server`와 기본 `flyway_schema_history`를 공유하면 V1/V2가 같은 version으로 충돌한다. → 전용 history 테이블로 분리:

```yaml
spring.flyway:
  table: flyway_schema_history_notification
  baseline-on-migrate: true    # 공유 스키마 non-empty
  baseline-version: "0"        # V1부터 적용 (기본 1이면 V1 skip)
```

증상·복구 절차: [runbook/flyway-shared-db-migration-failure.md](../runbook/flyway-shared-db-migration-failure.md).

## 6. 외부 스키마 의존성 (k8s 밖 — 백엔드 책임)

리더는 jOOQ DSL이지만 외부 테이블은 codegen 대상이 아니라(이름 기반 참조) **부팅 시점엔 검증되지 않는다**. 스키마 불일치는 기동이 아니라 **cron 잡 첫 실행에서 SQL 에러**로 드러난다.

| 테이블 · 컬럼 | 사용 잡 | 방향 |
| --- | --- | --- |
| `tb_notification.user_id`, `.device_token`, `.push_agreed` | 전체 | read |
| `tb_photo_image.user_id`, `.created_at`, `.deleted_at` | WEEKLY_REMINDER, HOLIDAY_EXPLORE | read |
| `tb_notification_hist.user_id`, `.type`, `.title`, `.body`, `.link` | 전체(발송 성공분) | **write** |

> FCM 토큰은 현재 `tb_notification.device_token` **컬럼**을 가정한다. 백엔드 [#291](https://github.com/Team-Neki/Team-Neki-Server/issues/291)이 **별도 테이블**로 구현되면 쿼리와 불일치하므로 머지 전 스키마 정합성 확인이 필요하다.
>
> `tb_notification_hist`는 백엔드([Team-Neki-Server V22](https://github.com/Team-Neki/Team-Neki-Server) `V22__create_notification_hist_table.sql`) 소유. 앱의 "최근 알림" 피드(`GetRecentNotificationsUseCase`)가 읽으므로 배치 발송분도 여기 적재한다(백엔드 `SendPushUseCase`와 대칭). **읽기 외부 테이블과 달리 write라 스키마 드리프트가 부팅이 아니라 발송 시 SQL 에러**로 드러난다 → 적재를 REQUIRES_NEW+try/catch로 격리해 실패해도 발송·`notification_log`를 지킨다(best-effort, [batch-pipeline.md §1](batch-pipeline.md), code-notes H-3). `type`은 우리 `NotificationType.name`(WEEKLY_REMINDER 등)을 그대로 넣으므로 앱의 type 코드 렌더링과 정합이 필요하면 앱팀과 확인.

## 7. 정합성 테스트

- `SchemaMigrationValidationTest` — "jOOQ 생성 타입 ↔ Flyway 스키마" 정합 검증(Flyway 활성). 후속 마이그레이션이 소유 테이블을 바꾸면 codegen `scripts`에 추가해야 하며, 누락 시 이 테스트가 실패해 알린다.
- `FlywayHistoryIsolationTest` — prod 조건(충돌 history + non-empty 스키마) 재현: 전용 테이블+baseline 0으로 V1부터 정상 적용, `baseline-on-migrate=false`는 non-empty에서 실패함을 검증.

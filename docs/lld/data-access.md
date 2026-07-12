# LLD — 데이터 접근 (jOOQ + Flyway)

결정·근거는 [ADR 0001](../adr/0001-jooq-over-jpa.md). 본 문서는 현행 구현 세부를 다룬다.

## 1. 소유권 경계 = 관리 경계

| 구분 | 테이블 | 관리 |
| --- | --- | --- |
| **앱 소유** | `notification_log`, `holiday` | Flyway V1 마이그레이션 + jOOQ **codegen 대상**(생성 타입으로 타입세이프 쿼리) |
| **Spring Batch 메타** | `BATCH_*` | Flyway V2 마이그레이션 |
| **외부 소유(read-only)** | `tb_notification`, `tb_photo_image` | 스키마 관리·codegen **안 함**. jOOQ **plain SQL**로 조회 |

- 스키마 SSOT: `apps/batch/src/main/resources/db/migration/V1__notification_schema.sql`, `V2__spring_batch_schema.sql`.
- codegen: 라이브 DB 없이 Flyway V1 DDL에서 `DDLDatabase`로 생성 → `com.neki.notification.infra.jooq`(`build/` 하위, 미추적).
- 소유↔외부 모두 단일 `DSLContext` API로 통일(JdbcTemplate 혼용 제거).

## 2. 소유 테이블 스키마

### `notification_log`
`id, user_id, notification_type, message_tone, variable_applied, title, body, business_date, status, sent_at`
- `UNIQUE(user_id, notification_type, business_date)` — 중복 발송 최종 방어선.
- `INDEX(business_date)`.
- `status`: 발송 라이프사이클(`SENT`/`FAILED`/`DEAD`/`SKIPPED`, `NotificationStatus`). V1의 `fcm_result`를 V3에서 리네임([ADR 0002](../adr/0002-notification-log-status-single-table.md)).

### `holiday`
`id, holiday_date, name, notify_offset_days`
- `UNIQUE(holiday_date)` — 멱등 upsert 기준.
- `notify_offset_days`: 발송 오프셋(전날=-1, 당일=0). 발송일 = `holiday_date + notify_offset_days`.

## 3. 어댑터

| 어댑터 | 포트 | 방식 | 비고 |
| --- | --- | --- | --- |
| `NotificationLogStoreAdapter` | `NotificationLogStore` | jOOQ 생성 타입 | `insertInto`/`fetchExists`. `sentAt` 없으면 적재 시각 주입(UTC). `clock` 빈 주입 강제. |
| `HolidayCalendarAdapter` | `HolidayCalendar` | jOOQ 생성 타입 | businessDate ±SCAN_WINDOW 후보 조회 → `notifyDate==businessDate` 선택 |
| `HolidayStoreAdapter` | `HolidayStore` | jOOQ 생성 타입 | `onConflict(HOLIDAY_DATE).doUpdate()` 멱등 upsert |
| `WeeklyReminderTargetReader` | (Reader) | plain SQL | 7일 전 업로드 EXISTS + `[최근 업로드 요일]` |
| `WeekendExploreTargetReader` | (Reader) | plain SQL | `push_agreed=true` 전원 |
| `HolidayExploreTargetReader` | (Reader) | plain SQL | 최근 1달 업로드 EXISTS + `[공휴일명]` |

공통 페이징 골격은 `TargetReaderSupport`:
- `PAGING_PREDICATE = "n.push_agreed = true AND n.user_id > ?"` — **동의 필터의 단일 출처** + keyset 커서.
- `PAGING_TAIL = "ORDER BY n.user_id LIMIT ?"`.
- `sendTarget(record, variables)` — `Record` → `SendTarget` 매퍼.

## 4. 렌더 케이스 (`JooqConfig`) — 함정 주의

codegen(`DDLDatabase`)이 식별자를 **대문자**로 생성한다. PostgreSQL은 인용 없는 식별자를 **소문자로 폴딩**하므로, 런타임에 그대로 렌더하면 `"NOTIFICATION_LOG"."USER_ID"`(인용 대문자)로 나가 실제 소문자 컬럼과 어긋난다.

→ `RenderNameCase.LOWER` + `RenderQuotedNames.NEVER`(+`renderSchema=false`)로 렌더. `Settings` 빈이 아니라 Spring Boot의 `DefaultConfigurationCustomizer`로 적용(전자는 자동구성에 안 먹힘). plain SQL은 verbatim 전달이라 이 설정 영향을 받지 않는다.

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

리더는 StepScope plain SQL이라 **부팅 시점엔 검증되지 않는다**. 스키마 불일치는 기동이 아니라 **cron 잡 첫 실행에서 SQL 에러**로 드러난다.

| 테이블 · 컬럼 | 사용 잡 |
| --- | --- |
| `tb_notification.user_id`, `.device_token`, `.push_agreed` | 전체 |
| `tb_photo_image.user_id`, `.created_at`, `.deleted_at` | WEEKLY_REMINDER, HOLIDAY_EXPLORE |

> FCM 토큰은 현재 `tb_notification.device_token` **컬럼**을 가정한다. 백엔드 [#291](https://github.com/Team-Neki/Team-Neki-Server/issues/291)이 **별도 테이블**로 구현되면 쿼리와 불일치하므로 머지 전 스키마 정합성 확인이 필요하다.

## 7. 정합성 테스트

- `SchemaMigrationValidationTest` — "jOOQ 생성 타입 ↔ Flyway 스키마" 정합 검증(Flyway 활성). 후속 마이그레이션이 소유 테이블을 바꾸면 codegen `scripts`에 추가해야 하며, 누락 시 이 테스트가 실패해 알린다.
- `FlywayHistoryIsolationTest` — prod 조건(충돌 history + non-empty 스키마) 재현: 전용 테이블+baseline 0으로 V1부터 정상 적용, `baseline-on-migrate=false`는 non-empty에서 실패함을 검증.

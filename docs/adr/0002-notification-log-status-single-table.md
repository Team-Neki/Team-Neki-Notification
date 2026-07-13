# ADR 0002 — 발송 상태(status)를 notification_log 단일 테이블 컬럼으로 도입

- 상태: 채택(Accepted)
- 날짜: 2026-07-13
- 관련: [ADR 0001 — jOOQ + Flyway](0001-jooq-over-jpa.md), `docs/lld/batch-pipeline.md`

## 맥락

기존 `notification_log`는 발송 1건마다 `fcm_result`(SUCCESS/FAILED/SKIPPED)를 **insert 시점에 한 번 박아 넣는 append-once 불변 로그**였다. 라이프사이클 상태나 재시도 개념이 없었고, 실패한 대상은 행으로 남지만 "왜 실패했는지(영구/일시)"를 구분하지 못했다.

상태를 도입하는 방식으로 두 안을 검토했다.

- **A. `notification_log`에 `status` 컬럼 추가** — 행을 (갱신 가능한) 상태 머신으로 삼는 단일 테이블.
- **B. 별도 `send_task`/outbox 테이블 분리** — 불변 감사 로그(`notification_log`)와 가변 오케스트레이션 상태를 분리.

## 결정

**A안을 채택한다.** `notification_log`의 `fcm_result` 컬럼을 라이프사이클 `status`로 승격(리네임)하고, 발송 상태는 이 단일 테이블의 컬럼으로 표현한다.

- 도메인 열거형 `FcmResult`(SUCCESS/FAILED/SKIPPED)를 **`NotificationStatus`(SENT/FAILED/DEAD/SKIPPED)** 로 대체한다.
- `notification_log.fcm_result` → `notification_log.status`로 리네임(Flyway V3). 기존 값 `SUCCESS`는 `SENT`로 백필한다.
- **PENDING**(발송 전 클레임)은 지금 도입하지 않는다. claim-first outbox(중복 창 제거)를 도입할 때 추가한다.

### 상태 값

| status | 의미 | 재시도 |
| --- | --- | --- |
| `SENT` | 실발송 성공(`FcmPushSender`) | — |
| `FAILED` | 일시적 실패(네트워크/`UNAVAILABLE`/`INTERNAL` 등) | 향후 재시도 대상 |
| `DEAD` | **영구 실패**(무효/만료 토큰: `UNREGISTERED`, `INVALID_ARGUMENT`, `SENDER_ID_MISMATCH`, `THIRD_PARTY_AUTH_ERROR`) | 재시도 금지 |
| `SKIPPED` | 무발송 모드(`LoggingPushSender`, `fcm.enabled=false`) | — |

`DEAD`는 브로커 DLQ 없이 **RDB가 데드레터 역할**을 하도록 하는 종료 상태다.

## 근거

- **노티 도메인 특성상 불변 감사 로그가 과하다.** 알림 발송 이력은 append-only로 영구 보존할 만큼 중요도가 높지 않고, 실패 시 추가 재처리로 얻는 가치도 제한적이다(무효 토큰은 재시도 무의미, 배치 특성상 지연 허용). 따라서 감사 로그와 상태를 **분리(B)** 해서 얻는 이점보다 단일 테이블(A)의 단순함이 낫다.
- **테이블/조인 최소화.** 소유 테이블 하나(`notification_log`)만 유지 → jOOQ codegen·마이그레이션·쿼리가 단순.
- **DEAD로 "재처리 한계"를 스키마에 명시.** 영구 실패를 종료 상태로 못박아, 무의미한 재시도를 구조적으로 배제한다.
- **점진적 확장 여지 유지.** 상태 컬럼이 생겼으므로, 이후 재시도 잡·PENDING(claim-first)·`last_error`/`retry_count`는 추가 마이그레이션으로 얹을 수 있다(스키마 재설계 불필요).

## 범위 (이번 변경)

포함:
- `NotificationStatus` 도입, `status` 컬럼 리네임(V3), `DEAD` 분류(FCM permanent 에러 매핑).
- 발송 결과 요약 로깅을 status 기준으로 집계.

**제외(후속)**:
- **재시도 잡** — `status=FAILED` 재발송 루프. 지금은 실패 건이 그대로 남고 자동 재시도하지 않는다(기존 동작 유지).
- **PENDING/claim-first outbox** — at-least-once 중복 창 제거. `docs/lld/batch-pipeline.md §5` 참조.
- `last_error`(FCM 에러코드), `retry_count`, `next_attempt_at` 컬럼 — 재시도 도입 시 추가.

## 결과

- **dedup 동작 불변**: `alreadySent`는 여전히 "(user, type, businessDate) 행 존재"로 판정한다. 즉 `FAILED`/`DEAD` 행도 재발송을 막는다(자동 재시도 없음, 위 "재처리 한계"와 정합). 재시도 잡을 도입하면 이 판정을 `status=SENT` 기준으로 좁힌다.
- **관측성 향상**: `WHERE status='DEAD'`로 무효 토큰 대상을, `='FAILED'`로 일시 실패를 구분 조회 가능. 실패 "사유(에러코드)"는 아직 앱 로그(WARN)에만 남는다(DB 미저장, 후속).
- **마이그레이션**: V3가 `fcm_result`를 `status`로 리네임하고 `SUCCESS→SENT` 백필. jOOQ codegen `scripts`에 V3를 추가(누락 시 `SchemaMigrationValidationTest` 실패로 검출).

## 대안

- **B. 별도 outbox 테이블**: 감사 로그 불변성 + 관심사 분리가 필요할 때 적합하나, 노티 중요도상 과설계 → 기각(위 근거).
- **브로커 DLQ(Kafka/SQS)**: 스케줄 배치·단일 인스턴스·유한 볼륨에는 인프라 과잉 → 기각. `DEAD` 상태의 RDB 행이 동일 역할.
- **`fcm_result` + `status` 병존**: 두 컬럼이 사실상 중복(무재시도 시 1:1) → 단일 `status`로 통합.

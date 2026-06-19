# Team Neki Notification

네키(Neki) 사용자에게 **시간 기반 푸시 알림**을 스케줄링으로 발송하는 배치 애플리케이션.
Kotlin + Spring Boot + Spring Batch 기반이며, 발송 대상 조회는 공유 PostgreSQL DB에서, 발송은 FCM으로 처리한다.

## 알림 종류 (이번 스프린트 범위)

푸시(마케팅) 수신 동의 사용자를 공통 대상으로 한다.

| 알림 | 트리거 | 대상 | 개인화 변수 |
| --- | --- | --- | --- |
| **아카이빙 1주일 리마인드** (`WEEKLY_REMINDER`) | 매일 20:00 | 7일 전 사진 업로드 이력 + 동의 | `[최근 업로드 요일]` |
| **주말 전 탐색** (`WEEKEND_EXPLORE`) | 금/토/일 지정 시각 | 전체 대상자 + 동의 | 없음 |
| **연휴/공휴일 탐색** (`HOLIDAY_EXPLORE`) | 매일 깨워 발송일 판정 | 최근 1달 업로드 이력 + 동의 | `[공휴일명]` |

> 범위 제외: 길찾기 후 아카이빙 유도(스펙 아웃), 브랜드 업데이트(별도 API 직접 호출).
> HOLIDAY_EXPLORE의 "지도 사용 이력" 조건은 데이터 소스 부재로 보류(이슈 [#292](https://github.com/Team-Neki/Team-Neki-Server/issues/292)).

## 기술 스택

| 영역 | 선택 |
| --- | --- |
| 언어 / 런타임 | Kotlin 2.0.21, JDK 21 |
| 프레임워크 | Spring Boot 3.4.5, Spring Batch |
| 빌드 | Gradle 8.14 (Kotlin DSL, 버전 카탈로그) |
| DB | PostgreSQL (공유 DB, read-only 조회 + 자체 테이블 write) |
| 마이그레이션 | Flyway (자체 소유 테이블, `apps:batch`의 `db/migration` V1~) |
| 읽기 | Jdbc projection (keyset 페이징) |
| 쓰기 | jOOQ (`notification_log`) — codegen은 Flyway V1 DDL에서 생성 ([ADR 0001](docs/adr/0001-jooq-over-jpa.md)) |
| 푸시 | Firebase Admin SDK 9.4.3 (FCM 직접 호출) |
| 테스트 | JUnit5, MockK, Testcontainers(PostgreSQL), Kover |

## 모듈 구조

```
apps:batch/          부트 jar — Batch Job/Step 조립, @Scheduled, in/out 어댑터, 설정, Flyway
   └─ depends on → domain, modules:fcm, modules:scheduling
domain/              순수 Kotlin — 모델·정책·도메인서비스 + application 포트(out). 프레임워크 의존성 0 (불변식)
modules:fcm/         Firebase Admin 초기화 설정
modules:scheduling/  스케줄링 설정(@EnableScheduling)
```

**원칙**: `domain`은 포트(`application/port/out`)만 정의하고, `apps:batch`의 어댑터(`adapter/in`·`adapter/out`)가 구현(Jdbc 읽기 / jOOQ 쓰기 / FCM)을 채운다. `apps:batch`가 와이어링한다.

## 공통 발송 파이프라인

```
Reader (Jdbc projection)  → 발송 대상 + 변수원천값 + FCM 토큰 (keyset 페이징)
Processor                 → 변수 치환(없으면 기본 문구) → 동의 최종 재확인 → 당일 중복 발송 스킵
Writer                    → FCM 발송 + notification_log 적재(jOOQ)
```

- 문구 톤(정보형/친근형/제안형)은 `userId floorMod 3`으로 결정적 배정하여 톤별 클릭률 A/B 분석에 활용.
- 중복 방지: `notification_log (user_id, notification_type, business_date)` unique 제약.

## 빌드 & 실행

JDK 21 필요. 래퍼가 포함되어 있어 별도 Gradle 설치 불필요.

```bash
./gradlew build              # 전체 빌드 + 테스트
./gradlew :domain:test       # 도메인 단위 테스트
./gradlew koverXmlReport     # 커버리지 리포트 (build/reports/kover/)
```

> 테스트는 Testcontainers로 PostgreSQL 컨테이너를 띄우므로 **Docker가 실행 중**이어야 한다.

## 테스트 / 품질 게이트

TDD(테스트 우선)로 개발한다. 커버리지 목표:

| 영역 | 목표 |
| --- | --- |
| 도메인(순수 로직) | line ≥ 90% / branch ≥ 85% |
| infra 어댑터 | line ≥ 80% (Testcontainers 통합 포함) |
| 배치 Job | E2E 시나리오(happy/skip/dedup/동의필터) 커버 |

## 외부 의존성 (Team-Neki-Server)

발송 대상은 백엔드 공유 DB(`Team-Neki-Server`, Flyway V1~V20)에서 조회한다.

| 항목 | 상태 | 영향 |
| --- | --- | --- |
| 마케팅 동의 약관 (PR [#290](https://github.com/Team-Neki/Team-Neki-Server/pull/290), V19/V20) | ✅ 병합됨 | 동의 판정(`TB_USER_TERM_AGREEMENT.withdrawn_at IS NULL`)이 의존 |
| FCM 토큰 테이블 (이슈 [#291](https://github.com/Team-Neki/Team-Neki-Server/issues/291)) | 🔴 미해결(open) | 실제 발송 경로 블로킹 |
| 지도/길찾기 이력 (이슈 [#292](https://github.com/Team-Neki/Team-Neki-Server/issues/292)) | ⏸ 보류(open) | HOLIDAY_EXPLORE 타겟 축소 |

> 우리 소유 테이블(`notification_log`, `holiday`, `BATCH_*`)은 공유 DB Flyway 이력과 충돌하지 않도록 `apps:batch`의 자체 Flyway 경로(`db/migration` V1·V2)로 관리한다. 공휴일 데이터는 DB 의존을 끊고 CSV 소스(`CsvHolidaySource`)에서 동기화한다.

## 배포 / 실행 설정

> ⚠️ 운영 플래그는 모두 기본 `false`다. `@ConditionalOnProperty`로 **기동 시 1회만 평가**(빈 생성 게이팅)되므로, 값 변경은 **pod 재시작**으로만 반영된다 — 런타임 토글 불가.

### 기능 플래그

| 프로퍼티 | env (k8s) | 기본 | 효과 |
| --- | --- | --- | --- |
| `neki.fcm.enabled` | `NEKI_FCM_ENABLED` | `false` | `true`=`FcmPushSender`(실제 발송), `false`=`LoggingPushSender`(로그만) |
| `neki.batch.scheduling-enabled` | `NEKI_BATCH_SCHEDULINGENABLED` | `false` | `true`라야 `@Scheduled` cron 스케줄러가 동작 (안 켜면 잡이 영원히 안 돎) |
| `neki.batch.holiday-sync-enabled` | `NEKI_BATCH_HOLIDAYSYNCENABLED` | `false` | `true`면 기동 시 번들 CSV→`holiday` 테이블 시드 (없으면 HOLIDAY_EXPLORE 빈 발송) |

### 필수 외부 설정 (k8s)

| 항목 | 설정 | 비고 |
| --- | --- | --- |
| DataSource | `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | `application.yml`에 datasource 블록이 없으므로 **주입 필수** |
| DB 권한 | 연결 계정에 `CREATE TABLE` | 기동 시 Flyway가 `notification_log`·`holiday`·`BATCH_*`를 생성(`baseline-on-migrate=true`) |
| FCM 자격증명 | 서비스계정 JSON을 **Secret 볼륨 마운트** + `NEKI_FCM_CREDENTIALSLOCATION=file:/path/sa.json` | `fcm.enabled=true`일 때 빈 생성 시점에 파일을 읽으므로 **파일이 실제 존재해야 기동 성공**(없으면 CrashLoop). env 문자열만으론 불가 |

### 외부 스키마 의존성 (k8s 밖 — 백엔드 책임)

발송 대상 조회는 공유 DB의 외부 소유 테이블에 의존한다. 이 스키마가 맞지 않으면 **기동은 되지만 cron 잡 첫 실행에서 SQL 에러로 실패**한다(리더는 StepScope plain SQL이라 부팅 시점엔 검증되지 않음).

| 테이블 · 컬럼 | 사용 잡 |
| --- | --- |
| `tb_notification.user_id`, `.device_token`, `.push_agreed` | 전체 |
| `tb_photo_image.user_id`, `.created_at` | WEEKLY_REMINDER, HOLIDAY_EXPLORE |

> FCM 토큰은 현재 리더가 `tb_notification.device_token` **컬럼**을 가정한다. 백엔드 [#291](https://github.com/Team-Neki/Team-Neki-Server/issues/291)이 **별도 테이블**로 구현되면 쿼리와 불일치하므로 머지 전 스키마 정합성 확인이 필요하다.

## 문서

- [설계·구현 계획](docs/superpowers/specs/2026-06-09-neki-notification-batch-design.md)
- [알림 문구·변수 사양 (SSOT)](docs/superpowers/specs/notification-copy-spec.md)
- [공유 DB 데이터 소스·의존성](docs/superpowers/specs/2026-06-10-shared-db-data-source-and-dependencies.md)

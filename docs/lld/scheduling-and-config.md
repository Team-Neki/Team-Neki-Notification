# LLD — 스케줄링 / 설정 / 기능 플래그

## 1. 스케줄러

`NotificationJobScheduler`(`@ConditionalOnProperty neki.batch.scheduling-enabled=true`)가 cron으로 `NotificationJobLauncher`를 호출한다. cron 값과 타임존은 프로퍼티로 외부화.

| 잡 | 프로퍼티 | 현행 cron (`application.yml`) |
| --- | --- | --- |
| WEEKLY_REMINDER | `neki.batch.cron.weekly` | `0 0 20 * * *` (매일 20:00) |
| WEEKEND_EXPLORE | `neki.batch.cron.weekend` | `0 0 18 * * FRI,SAT,SUN` (금·토·일 18:00) |
| HOLIDAY_EXPLORE | `neki.batch.cron.holiday` | `0 0 9 * * *` (매일 09:00, 발송일 판정) |

- 타임존: `scheduling.zone`(기본 `Asia/Seoul`). `modules:scheduling`의 `SchedulingConfiguration`이 `@EnableScheduling` + 운영 타임존 `Clock` 빈 제공.
- **cron 기반이라 재기동만으로 잡이 즉시 실행되지 않는다**(rollout 재전송 방지의 1차 요인 — `batch-pipeline.md §6`).

## 2. 잡 기동 (`NotificationJobLauncher`)

- `businessDate` = 운영 타임존 기준 오늘(`LocalDate.now(clock)`), `launchedAt` = `clock.millis()`로 JobInstance 유일화.
- 기동 직전 `JobExplorer.findRunningJobExecutions(job.name)`이 비어있지 않으면 **건너뛰고 null 반환**(중복/동시 기동 방어, 단일 JVM 한정).
- `launchByName(jobName, businessDate?)`로 이름 기동도 지원(테스트 API가 사용).

## 3. 인바운드 트리거 3종

| 트리거 | 활성 조건 | 용도 |
| --- | --- | --- |
| `NotificationJobScheduler` | `neki.batch.scheduling-enabled=true` | cron 정기 발송 |
| `HolidayLoader` (`@EventListener(ApplicationReadyEvent)`) | `neki.batch.holiday-sync-enabled=true` | 기동 완료 시 공휴일 CSV→인메모리 적재 1회 |
| `TestNotificationController` | `neki.test-api.enabled=true` | 수동 잡 기동/단건 푸시 스모크 (pod 내부 전용) |

> 주의: `TestNotificationController`(`POST /test/notifications/jobs/{jobName}`, `POST /test/notifications/push`)는 인증 없는 **pod 내부 전용** API다. `neki.test-api.enabled=true`인 pod에서만 빈이 등록되며 k8s Service/Ingress로 외부 노출 금지. 호출 시 실제 배치·FCM 발송이 일어난다(prod는 `fcm.enabled=true`). 잡 트리거는 `jobLauncher` 동기 실행이라 종료까지 응답이 블로킹된다. 단건 push는 `notification_log`에 기록하지 않는다.

## 4. 기능 플래그 (`@ConditionalOnProperty`)

**모든 플래그는 기동 시 1회만 평가**(빈 생성 게이팅)되므로, 값 변경은 **pod 재시작**으로만 반영된다 — 런타임 토글 불가.

| 프로퍼티 | base 기본 | prod(`application-prod.yml`) | 효과 |
| --- | --- | --- | --- |
| `neki.fcm.enabled` | `false` | `true` | `true`=`FcmPushSender`(실발송), `false`=`LoggingPushSender`(로그만, `SKIPPED`) |
| `neki.batch.scheduling-enabled` | `false` | `true` | `@Scheduled` 스케줄러 동작 여부 |
| `neki.batch.holiday-sync-enabled` | `false` | `true` | 기동 완료 시 공휴일 CSV **인메모리 적재** 여부 |
| `neki.batch.holiday-csv` | `holidays.csv` | (동일) | 공휴일 원천 CSV 클래스패스 경로 |
| `neki.test-api.enabled` | `false` | (미설정 → `false`) | 수동 트리거 테스트 API(`TestNotificationController`) 빈 등록 여부. pod 내부 전용 — 필요 시 prod에서 명시적 `true` |

## 5. 프로파일 / 필수 외부 설정

운영은 `SPRING_PROFILES_ACTIVE=prod`(Dockerfile 기본)로 `application-prod.yml`을 활성화한다. 이 파일이 위 플래그를 `true`로 오버라이드하고 다음을 설정한다.

| 항목 | 운영 설정 방식 | 필수 주입 |
| --- | --- | --- |
| DataSource | `application-prod.yml`에 **ENC(...)** 로 URL/사용자/비밀번호 내장(서버 앱과 동일 공유 DB) | env **`JASYPT_PASSWORD`**(복호화 키, `neki-secrets`) |
| FCM 자격증명 | `credentials-location: file:/etc/firebase/firebase-service-account.json` 하드코딩 | 해당 경로에 **서비스계정 JSON Secret 볼륨 마운트**(없으면 빈 생성 실패 → CrashLoop) |
| DB 권한 | 연결 계정에 `CREATE TABLE`(기동 시 Flyway가 소유 테이블 생성) | — |

> base `application.yml`에는 datasource 블록이 없다. 테스트는 Testcontainers가 주입, 운영은 prod 프로파일이 담당한다. (env `SPRING_DATASOURCE_*` 오버라이드도 Spring relaxed binding으로 가능하지만, **현행 운영 경로는 prod 프로파일의 ENC 값 + JASYPT_PASSWORD**다.)

## 6. 헬스 / 종료

- Actuator health만 노출(8080). 프로브 그룹: `liveness=livenessState`, `readiness=readinessState,db`.
- `server.shutdown: graceful` — k8s `preStop(sleep 10)` + `terminationGracePeriodSeconds(30)`와 정합.

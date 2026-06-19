# Code Notes

코드에 있던 설명·근거 주석을 이 문서로 모았다. 주석이 코드와 함께 stale해지는 것을 막고, 설계 의도를 한 곳에서 보기 위함이다. 각 항목은 `파일:라인 (요소)` anchor로 어느 코드에 적용되는지 가리킨다.

> 주의: 라인 번호는 이 문서 작성 시점 기준이며 코드 변경에 따라 이동할 수 있다. anchor는 라인보다 **요소 이름(클래스·함수)** 을 우선 신뢰하라. 스펙 참조(`batch-design §`, `copy-spec §`, `shared-db §`)는 `docs/superpowers/specs/` 문서를 가리킨다.

---

## domain — 포트 (application/port/out)

- `domain/.../application/port/out/HolidayCalendar.kt:6` (`interface HolidayCalendar`) — 공휴일 발송일 판정 포트(batch-design §P5). infra가 `holiday` 테이블 조회로 구현한다.
  - `:7` (`holidayToNotifyOn`) — `businessDate`가 어떤 공휴일의 발송일(notifyDate)이면 그 공휴일을, 아니면 null을 반환. HOLIDAY_EXPLORE Job이 매일 깨어나 이 판정으로 발송 여부와 `[공휴일명]`을 결정한다.
- `domain/.../application/port/out/NotificationLogStore.kt:7` (`interface NotificationLogStore`) — 발송 이력 저장/조회 포트(batch-design §4). infra가 JPA로 구현. 이력 조회(중복 판정의 원천)는 포트 책임이고, "이미 발송됨" 불리언을 받은 뒤의 발송 제외 판정은 도메인(NotificationProcessor) 책임(copy-spec §7).
  - `:8` (`alreadySent`) — (userId, type, businessDate) 키로 이미 발송된 이력이 있는지.
  - `:10` (`save`) — 발송 이력 1건 적재. unique (user_id, notification_type, business_date) 제약으로 최종 중복 방지.
- `domain/.../application/port/out/PushSender.kt:6` (`fun interface PushSender`) — 푸시 발송 포트(batch-design §4). infra가 Firebase Admin SDK 어댑터로 구현.
  - `:7` (`send`) — 단일 디바이스 토큰으로 메시지 발송. 무효 토큰·전송 실패는 `FcmResult.FAILED`.

## domain — 모델 (domain/model)

- `domain/.../domain/model/FcmResult.kt:3` (`enum FcmResult`) — FCM 발송 결과(batch-design §6 notification_log.fcm_result).
- `domain/.../domain/model/Holiday.kt:5` (`data class Holiday`) — 공휴일/연휴(batch-design §6 holiday). `notifyOffsetDays`: 발송 시점 오프셋(전날=-1, 당일=0), 발송일(notifyDate) = date + notifyOffsetDays. `notifyDate` 프로퍼티는 이 공휴일을 알릴 실제 발송일.
- `domain/.../domain/model/MessageTone.kt:3` (`enum MessageTone`) — 메시지 톤. **선언 순서가 유효**(copy-spec §5): `MessageTone.entries[floorMod(userId, 3)]`.
- `domain/.../domain/model/MessageVariable.kt:3` (`enum MessageVariable`) — 문구 치환 변수(copy-spec §3).
- `domain/.../domain/model/NotificationType.kt:3` (`enum NotificationType`) — In-scope 알림 타입(copy-spec §1). 같은 파일의 기본(폴백) 톤 매핑(`:12` 부근) — 알림 타입별 기본 톤(copy-spec §1), 폴백 톤 템플릿은 변수를 필요로 하지 않는다.
- `domain/.../domain/model/NotificationLog.kt:6` (`data class NotificationLog`) — 발송 이력(batch-design §6 notification_log). 순수 도메인 모델 — infra가 JPA 엔티티로 매핑. 중복 방지 키: (userId, notificationType, businessDate)(copy-spec §7).
  - `:18` (`companion object of()`) — 발송 결과로부터 이력 1건 생성. `sentAt`은 infra 적재 시점에 채워진다.
- `domain/.../domain/model/PreparedNotification.kt:5` (`data class PreparedNotification`) — 발송 확정된 1건(Processor → Writer 전달 DTO, batch-design §5). Writer가 `target`의 토큰으로 `message`를 발송한 뒤 그 결과로 NotificationLog를 적재한다.
- `domain/.../domain/model/RenderedMessage.kt:3` (`data class RenderedMessage`) — 렌더링 결과(copy-spec §6).
- `domain/.../domain/model/SendTarget.kt:3` (`data class SendTarget`) — 발송 대상 1건(copy-spec §8).
- `domain/.../domain/model/SendDecision.kt` (`SendDecision`) — 발송 대상 1건에 대한 처리 판정(batch-design §5 Processor 단계). `Send`: 발송 확정(렌더링된 문구를 들고 Writer로). `Skip`: 발송 제외(사유 동반).
  - `:9` (`enum SkipReason`) — 발송 제외 사유(batch-design §5 ②③). `NO_CONSENT`: 푸시 미동의(TB_NOTIFICATION.push_agreed=false). `ALREADY_SENT`: 당일 동일 (userId, type, businessDate) 이미 발송됨(copy-spec §7).

## domain — 정책/서비스 (domain/policy, domain/service)

- `domain/.../domain/policy/MessageRenderer.kt:9` (`object MessageRenderer`) — 문구 렌더링(copy-spec §4 템플릿 + §6 치환/폴백 규칙). 순수 함수.
  - 템플릿 테이블(클래스 상단) — copy-spec §4의 9개 (type, tone) 템플릿을 verbatim 인코딩. **두 단계 `when`** 으로 표현해 NotificationType/MessageTone enum 망라성을 컴파일러가 강제한다.
  - `:67` (`render`) 치환 규칙: (1) 변수가 필요 없으면 그대로 렌더링 → (2) 변수값이 존재하고 비어있지 않으면 치환 → (3) 변수값 없음이면 기본(폴백) 톤 템플릿으로 폴백(폴백 템플릿은 변수 불필요).
- `domain/.../domain/policy/ToneAssignmentPolicy.kt:5` (`object ToneAssignmentPolicy`, `:6 assign`) — 유저별 결정적 톤 배정(copy-spec §5): `MessageTone.entries[floorMod(userId, 3)]`. 음수 userId는 `Math.floorMod`로 안전 처리.
- `domain/.../domain/service/NotificationProcessor.kt:10` (`object NotificationProcessor`, `:12 decide`) — 발송 대상 처리 판정(batch-design §5 Processor). 순수 함수. 순서: ① 푸시동의 확인 → ② 당일 중복 확인 → ③ 톤 배정 + 문구 렌더링. 이력 조회 자체는 포트(NotificationLogStore) 책임이고, 여기서는 그 결과(alreadySent)를 입력으로 받는다(copy-spec §7).

## modules/postgresql — 영속성 인프라

- `modules/postgresql/.../infra/persistence/HolidayEntity.kt:18` (`class HolidayEntity`) — 공휴일 JPA 엔티티(batch-design §6 holiday). 본 앱 소유, 수동 시드.
  - `:33` (`toDomain`) — 엔티티 → 도메인 `Holiday` 매핑.
- `modules/postgresql/.../infra/persistence/HolidayJpaRepository.kt:6` (`interface HolidayJpaRepository`) — holiday Spring Data JPA 리포지토리.
  - `:7` (`findByHolidayDateBetween`) — 발송일 판정 후보군: holiday_date가 [start, end] 범위인 공휴일.
- `modules/postgresql/.../infra/persistence/NotificationLogEntity.kt:33` (`class NotificationLogEntity`) — 발송 이력 JPA 엔티티(batch-design §6 notification_log). 중복 방지: UNIQUE(user_id, notification_type, business_date). 본 앱 소유 테이블 — DDL은 Flyway 마이그레이션이 소유(application.yml `ddl-auto=validate`). 스키마 정의는 `docs/code-notes.md`가 아니라 `apps/batch/src/main/resources/db/migration/V1__notification_schema.sql`.
  - `:69` (`companion object from()`) — 도메인 로그 → 엔티티 매핑(`sentAt` 미지정 시 적재 시각 주입).
- `modules/postgresql/.../infra/persistence/NotificationLogJpaRepository.kt:7` (`interface NotificationLogJpaRepository`, `:8 existsByUserIdAndNotificationTypeAndBusinessDate`) — notification_log Spring Data JPA 리포지토리.
- `modules/postgresql/.../infra/persistence/config/PostgresPersistenceConfig.kt:10` (`class PostgresPersistenceConfig`, `@Configuration :7`) — postgresql 영속성 모듈의 JPA 구성(config는 module 소유). 엔티티/리포지토리 스캔 범위를 이 모듈 패키지로 한정해 모듈을 자기완결적으로 만든다. 포트 구현 어댑터는 apps의 `adapter.out`으로 분리돼 있고, 여기서는 영속성 기술 구성(엔티티·Spring Data JPA 리포지토리 등록)만 책임진다.

## modules/fcm — FCM 기술 인프라

- `modules/fcm/.../infra/fcm/FirebaseConfig.kt:15` (`class FirebaseConfig`, `@Configuration :13`) — Firebase Admin SDK 초기화(batch-design §4, 기술 인프라 모듈). `neki.fcm.enabled=true`일 때만 자격증명(`neki.fcm.credentials-location`)을 읽어 `FirebaseApp`(`:18`)과 `FirebaseMessaging`(`:28`) 빈을 등록. "어떤 포트를 구현할지"는 앱(adapter/out) 책임이고, 이 모듈은 FCM 클라이언트 구성만 담당한다.

## modules/scheduling — 스케줄링 기술 인프라

- `modules/scheduling/.../infra/scheduling/SchedulingConfiguration.kt:12` (`class SchedulingConfiguration`, `@Configuration :10`) — 스케줄링 기술 인프라(도메인 무관, 재사용 가능 모듈). `@EnableScheduling`으로 `@Scheduled` 인프라를 켜고 운영 타임존 기준 `Clock` 제공. "무엇을 언제 돌릴지"(cron·활성화 토글)는 이 모듈이 아니라 각 앱의 adapter/in 정책. 그래서 앱 전용 속성(`neki.batch.*`)에 의존하지 않고 일반 속성 `scheduling.zone`만 사용한다.
  - `:14` (`clock`) — 운영 타임존 기준 시계. 기본 Asia/Seoul, `scheduling.zone`으로 재정의 가능.

## apps/batch — adapter/in (인바운드)

- `apps/batch/.../batch/adapter/in/scheduler/NotificationJobScheduler.kt:17` (`class NotificationJobScheduler`, `@Component :15`) — cron 스케줄 → Job 기동(batch-design §2/§P7). WEEKLY_REMINDER 매일 20:00 / WEEKEND_EXPLORE 금·토·일 / HOLIDAY_EXPLORE 매일 깨워 발송일 판정(공휴일 아니면 Job 내부 0건). `businessDate`는 운영 타임존 기준 오늘, `launchedAt`(ms)로 JobInstance를 매 기동 유일화.
  - `:36` (`launch`) — **중복/동시 기동 방어(C-1)**: `launchedAt` 유일화 때문에 같은 businessDate라도 매번 새 JobInstance가 생성돼 Spring Batch 중복 방지에 기댈 수 없다. 그래서 기동 직전 `JobExplorer.findRunningJobExecutions`로 동일 Job이 실행 중이면 트리거를 건너뛴다(이전 실행이 cron 주기를 넘긴 경우 방어). 이 가드는 **단일 JVM 인스턴스 내에서만** 유효 — 본 배치는 단일 인스턴스(k8s replica=1 등) 운영 전제이며 배포 매니페스트로 강제해야 한다. 다중 인스턴스는 ShedLock 등 분산 락으로 단일 발화를 별도 보장(발송 자체의 중복은 DB unique 제약만으로 못 막음).

## apps/batch — adapter/out (포트 구현 어댑터)

- `apps/batch/.../batch/adapter/out/HolidayCalendarAdapter.kt:10` (`class HolidayCalendarAdapter`) — `HolidayCalendar`의 JPA 구현(batch-design §P5). businessDate 주변 ±SCAN_WINDOW일의 공휴일을 후보로 조회한 뒤 `notifyDate(=date+notifyOffsetDays)==businessDate`인 공휴일 선택. 오프셋 계산을 Kotlin에서 처리해 DB 종속 날짜 연산 회피.
  - `MAX_OFFSET_DAYS = 2L` — 허용 notifyOffsetDays 절대값 상한(전날 -1 / 당일 0 + 여유). 시드 데이터 계약.
  - `SCAN_WINDOW = MAX_OFFSET_DAYS + 1` — 가드(B-6/L-4): 조회 윈도우를 계약보다 1일 넓게 잡아 `|offset|`이 상한을 1 초과한 데이터(±3)도 조용히 누락되지 않고 발송일에 매칭된다. 그런 후보는 WARN으로 로깅해 `MAX_OFFSET_DAYS` 상향/데이터 점검을 유도(SCAN_WINDOW 밖은 여전히 미조회이므로 조기 경고 목적).
- `apps/batch/.../batch/adapter/out/NotificationLogStoreAdapter.kt:14` (`class NotificationLogStoreAdapter`) — `NotificationLogStore`의 JPA 구현(batch-design §4 persistence-write). `sentAt`이 비어 있으면 적재 시각 주입. 동일 키 동시 적재는 DB unique 제약이 최종 방어선. `clock`은 기본값 없이 빈 주입 강제(B-4): 스케줄링 모듈이 제공하는 단일 `Clock`(Asia/Seoul) 빈을 일관되게 쓰고, 표면 의도와 실제 주입이 어긋나지 않게 한다(`Instant`라 결과는 동일하나 단일 시간 소스 원칙).
- `apps/batch/.../batch/adapter/out/fcm/FcmPushSender.kt:15` (`class FcmPushSender`) — `PushSender`의 Firebase Admin SDK 구현(batch-design §4). `neki.fcm.enabled=true`일 때만 활성(미설정/로컬은 LoggingPushSender가 대체). 토큰 단위 전송 실패는 배치를 중단시키지 않도록 `FcmResult.FAILED`로 흡수. `init` 로그(H-1): 실제 발송 모드임을 기동 로그로 명시해 LoggingPushSender(무발송)와 운영 상태를 구분.
- `apps/batch/.../batch/adapter/out/fcm/LoggingPushSender.kt:12` (`class LoggingPushSender`) — FCM 비활성(`neki.fcm.enabled`!=true) 환경용(로컬/테스트/드라이런). 실제 발송 없이 로그만 남기고 `FcmResult.SKIPPED` 반환. 컨텍스트가 항상 PushSender 빈을 갖도록 보장해 자격증명 없이도 앱 기동·배치 실행 가능. `init` 로그(H-1): 무발송 모드를 기동 시점에 WARN으로 크게 알려 운영에서 "전 건 SKIPPED"가 조용히 지나가지 않게 한다.
- `apps/batch/.../batch/adapter/out/read/HolidayExploreTargetReader.kt:11` (`class HolidayExploreTargetReader`) — HOLIDAY_EXPLORE 발송 대상 리더(shared-db §1/§5: 최근 1달 사진 업로드 이력 + 푸시동의). 지도사용 이력 데이터 부재(이슈 #292)로 업로드 이력만으로 대상 축소. `[공휴일명]`은 유저별이 아니라 실행(발송일) 단위 값이라 호출자가 주입한 `holidayName`을 모든 대상에 동일하게 채운다.
- `apps/batch/.../batch/adapter/out/read/WeekendExploreTargetReader.kt:9` (`class WeekendExploreTargetReader`) — WEEKEND_EXPLORE 발송 대상 리더(shared-db §1: 전체 대상자 + 푸시동의). 대상 = `push_agreed=true` 전원, 변수 없음. 공통 keyset 페이징 골격은 `TargetReaderSupport`에 위임.
- `apps/batch/.../batch/adapter/out/read/TargetReaderSupport.kt` (`object TargetReaderSupport`) — 세 TargetReader가 공유하는 페이징 골격 모음(B-1). 공통 술어 `PAGING_PREDICATE`(push 동의 + `user_id > :after` 키셋 커서) + 정렬·한도 `PAGING_TAIL`(`ORDER BY n.user_id LIMIT :limit`) + 공통 파라미터 바인딩 `pagingParams(after,limit)` + RowMapper `sendTarget(rs, variables)`(user_id/device_token→SendTarget). 각 Reader는 고유 조건(weekly의 7일 전 업로드, holiday의 최근 1달 EXISTS)과 변수만 덧붙인다. 추상화 인터페이스가 아니라 SQL 조각·매핑 함수 수준의 중복 제거(2-1 결정과 충돌 회피).
- `apps/batch/.../batch/adapter/out/read/WeeklyReminderTargetReader.kt:11` (`class WeeklyReminderTargetReader`) — WEEKLY_REMINDER 발송 대상 리더(shared-db §1: 7일 전 사진 업로드 이력 + 푸시동의). 대상 = `push_agreed=true`이면서 `businessDate-7일`에 업로드 이력이 있는 유저. `[최근 업로드 요일]` = 해당 유저 최근 업로드 일자의 요일(copy-spec §3).
- `apps/batch/.../batch/adapter/out/read/KoreanWeekday.kt` (`object KoreanWeekday`, `:18 recentUploadLabel`) — `[최근 업로드 요일]` 변수 포맷(copy-spec §3 예시 "지난 토요일"). 가정: 최근 업로드 일자의 요일에 "지난 " 접두사. 정확한 카피 톤은 추후 조정 가능하도록 이 한 곳에 격리.

## apps/batch — application/job (유스케이스 조립)

- `apps/batch/.../batch/application/job/NotificationStepFactory.kt:23` (`class NotificationStepFactory`, `@Component :22`) — 알림 배치 Job 3종의 공통 골격 조립(batch-design §5). 세 Job(WEEKEND/WEEKLY/HOLIDAY)은 Reader → Processor → Writer 청크 구조가 동일하고 Reader 쿼리와 `NotificationType`만 다르다. 동일한 청크/Job 조립 보일러플레이트를 한 곳에 모아 타입별 `~Job` 클래스는 자기 Reader·타입만 선언하게 한다(OCP: 타입 추가 = 클래스 추가). Writer는 무상태 싱글턴이라 팩토리가 직접 보유.
  - `:31 pagingReader` — keyset 페이징 Reader. `fetch`는 (afterUserId, pageSize) → 페이지.
  - `:34 processor` — 당일 중복 판정 + 도메인 발송 판정 Processor.
  - `:37 chunkStep` — Reader → Processor → (공통)Writer 청크 Step.
  - `:49 singleStepJob` — 단일 Step Job.
- `apps/batch/.../batch/application/job/WeekendExploreJob.kt:18` (`class WeekendExploreJob`, `@Configuration("weekendExploreJobConfig") :17`) — WEEKEND_EXPLORE Job(batch-design §5): 동의자 전원에게 주말 탐방 알림. 대상 = `push_agreed=true` 전원, 변수 없음. 골격 조립은 NotificationStepFactory에 위임.
- `apps/batch/.../batch/application/job/WeeklyReminderJob.kt:18` (`class WeeklyReminderJob`, `@Configuration("weeklyReminderJobConfig") :17`) — WEEKLY_REMINDER Job(batch-design §5): 7일 전 업로드 동의자에게 주간 리마인드. Reader가 `businessDate`(JobParameter)를 받아 7일 전 업로드 이력을 조회하고 `[최근 업로드 요일]`을 채운다. 골격 조립은 NotificationStepFactory에 위임.
- `apps/batch/.../batch/application/job/HolidayExploreJob.kt:19` (`class HolidayExploreJob`, `@Configuration("holidayExploreJobConfig") :18`) — HOLIDAY_EXPLORE Job(batch-design §P5): 공휴일 발송일에 최근 1달 업로드 동의자에게 알림. 매일 깨어나 `HolidayCalendar`로 발송일 여부를 판정. 발송일이 아니면 빈 Reader로 0건 처리, 발송일이면 `[공휴일명]`을 채워 발송. 골격 조립은 NotificationStepFactory에 위임.
  - `:26 holidayItemReader` — 발송일이 아니면(공휴일 매칭 없음) 아무 것도 읽지 않는다(빈 Reader 반환).

## apps/batch — application/step (배치 스텝 컴포넌트)

- `apps/batch/.../batch/application/step/NotificationItemProcessor.kt:12` (`class NotificationItemProcessor`) — 발송 대상 → 발송 확정 변환(batch-design §5 Processor). 당일 중복 여부를 `logStore`로 조회한 뒤 `NotificationProcessor`로 판정. Skip(미동의/중복)이면 null 반환해 청크에서 필터.
- `apps/batch/.../batch/application/step/NotificationItemWriter.kt:10` (`class NotificationItemWriter`) — 발송 + 이력 적재(batch-design §5 Composite Writer). 각 건을 FCM 발송하고 그 결과(SUCCESS/FAILED/SKIPPED)로 NotificationLog를 적재. 동일 키 동시 적재는 DB unique 제약이 최종 방어선.
- `apps/batch/.../batch/application/step/PagingSendTargetItemReader.kt:6` (`class PagingSendTargetItemReader`) — keyset 페이징 `ItemReader`(batch-design §5 Reader). `user_id` 오름차순으로 페이지를 당겨 1건씩 흘려보낸다. 빈 페이지를 만나면 소진으로 보고 종료. 단일 인스턴스·단일 스레드 Step 전제(분산 락 불필요, batch-design §3).

## apps/batch — test/architecture (아키텍처 제약)

- `apps/batch/.../architecture/PersistenceScanBoundaryTest.kt` (`class PersistenceScanBoundaryTest`) — 컴포넌트 스캔 이중화 가드(B-3/M-1, ArchUnit). 루트 `@SpringBootApplication`은 `com.neki.notification` 전역을 스캔하지만 `PostgresPersistenceConfig`의 `@EntityScan`/`@EnableJpaRepositories`는 `com.neki.notification.infra.persistence`로 한정한다. 두 선언은 현재 멱등이나, JPA 엔티티/리포가 이 패키지 밖으로 이동하면 모듈 config가 조용히 누락한다. 이 테스트가 "JPA 엔티티·Spring Data 리포는 `infra.persistence` 하위에 둔다"는 제약을 고정해 드리프트를 CI에서 차단한다(이중 구조는 유지하되 제약을 테스트로 못박는 B-3 (b)안).

# Code Notes

코드에 있던 설명·근거 주석을 이 문서로 모았다. 주석이 코드와 함께 stale해지는 것을 막고, 설계 의도를 한 곳에서 보기 위함이다. 각 항목은 `파일:라인 (요소)` anchor로 어느 코드에 적용되는지 가리킨다.

> 주의: 라인 번호는 이 문서 작성 시점 기준이며 코드 변경에 따라 이동할 수 있다. anchor는 라인보다 **요소 이름(클래스·함수)** 을 우선 신뢰하라. 스펙 참조 매핑: `copy-spec §` → `docs/prd/notification-copy-spec.md`, `batch-design §`·`shared-db §` → `docs/superpowers/specs/`(역사적 설계 기록). 현행 구현 기준 문서는 `docs/lld/`·`docs/prd/`이며 CLAUDE.md가 라우팅한다.

---

## domain — 포트 (application/port/out)

- `domain/.../application/port/out/HolidayCalendar.kt:6` (`interface HolidayCalendar`) — 공휴일 발송일 판정 포트(batch-design §P5). infra가 인메모리(`InMemoryHolidayRepository`)로 구현한다.
  - `:7` (`holidayToNotifyOn`) — `businessDate`가 어떤 공휴일의 발송일(notifyDate)이면 그 공휴일을, 아니면 null을 반환. HOLIDAY_EXPLORE Job이 매일 깨어나 이 판정으로 발송 여부와 `[공휴일명]`을 결정한다.
- `domain/.../application/port/out/NotificationLogStore.kt:7` (`interface NotificationLogStore`) — 발송 이력 저장/조회 포트(batch-design §4). infra가 jOOQ로 구현. 이력 조회(중복 판정의 원천)는 포트 책임이고, "이미 발송됨" 불리언을 받은 뒤의 발송 제외 판정은 도메인(NotificationProcessor) 책임(copy-spec §7).
  - `:8` (`alreadySent`) — (userId, type, businessDate) 키로 이미 발송된 이력이 있는지.
  - `:10` (`save`) — 발송 이력 1건 적재. unique (user_id, notification_type, business_date) 제약으로 최종 중복 방지.
- `domain/.../application/port/out/PushSender.kt:6` (`fun interface PushSender`) — 푸시 발송 포트(batch-design §4). infra가 Firebase Admin SDK 어댑터로 구현.
  - `:7` (`send`) — 단일 디바이스 토큰으로 메시지 발송. 무효 토큰·전송 실패는 `FcmResult.FAILED`.
- `domain/.../application/port/out/HolidaySource.kt` (`fun interface HolidaySource`) — 공휴일 **외부 원천** 포트(seed source). 현재 CSV(`CsvHolidaySource`), 추후 Google Sheet로 교체(issue #17). `load()` → `List<Holiday>`. 원천 적재(이 포트)와 도메인 판정 조회(`HolidayCalendar`)는 분리된 관심사다.
- `domain/.../application/port/out/HolidayStore.kt` (`interface HolidayStore`) — 공휴일 적재 포트. `upsertAll(holidays)` → holiday_date 기준 멱등 병합. infra가 인메모리(`InMemoryHolidayRepository`)로 구현(과거 jOOQ DB 어댑터에서 이관).
- `domain/.../application/port/out/SendTargetReader.kt` (`fun interface SendTargetReader`) — 발송 대상 조회 포트(batch-design §5 Reader). keyset 페이징의 "바인딩된 페이지 조회자" 형태로 `readPage(afterUserId, pageSize)`만 노출한다. 실행 종속 파라미터(businessDate·공휴일명)는 이 포트에 넣지 않고 **인바운드 어댑터(배치 Job 조립, adapter/in/batch)가 유형별 리더를 이 포트로 바인딩**한다. 그래서 발송(`PushSender`)·적재(`NotificationLogStore`)와 대칭으로 읽기도 포트를 통한다(구체 `read/*` 리더 직결 제거).

## domain — 모델 (domain/model)

- `domain/.../domain/model/FcmResult.kt:3` (`enum FcmResult`) — FCM 발송 결과(batch-design §6 notification_log.fcm_result).
- `domain/.../domain/model/Holiday.kt:5` (`data class Holiday`) — 공휴일/연휴(batch-design §6 holiday). `notifyOffsetDays`: 발송 시점 오프셋(전날=-1, 당일=0), 발송일(notifyDate) = date + notifyOffsetDays. `notifyDate` 프로퍼티는 이 공휴일을 알릴 실제 발송일.
- `domain/.../domain/model/MessageTone.kt:3` (`enum MessageTone`) — 메시지 톤. **선언 순서가 유효**(copy-spec §5): `MessageTone.entries[floorMod(userId, 3)]`.
- `domain/.../domain/model/MessageVariable.kt:3` (`enum MessageVariable`) — 문구 치환 변수(copy-spec §3).
- `domain/.../domain/model/NotificationType.kt:3` (`enum NotificationType`) — In-scope 알림 타입(copy-spec §1). 같은 파일의 기본(폴백) 톤 매핑(`:12` 부근) — 알림 타입별 기본 톤(copy-spec §1), 폴백 톤 템플릿은 변수를 필요로 하지 않는다.
- `domain/.../domain/model/NotificationLog.kt:6` (`data class NotificationLog`) — 발송 이력(batch-design §6 notification_log). 순수 도메인 모델 — infra(jOOQ 어댑터)가 notification_log 행으로 매핑. 중복 방지 키: (userId, notificationType, businessDate)(copy-spec §7).
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

## 영속성 (jOOQ)

jOOQ 마이그레이션(PR #13~)으로 JPA(Hibernate)·`modules/postgresql` 모듈을 제거하고 영속성을 `apps/batch`로 통합했다(결정·근거: `docs/adr/0001-jooq-over-jpa.md`). 소유 테이블(`notification_log`)은 Flyway V1 DDL에서 jOOQ codegen으로 타입 생성(`com.neki.notification.infra.jooq`, `build/` 하위·미추적), 외부 소유 테이블(`tb_notification`/`tb_photo_image`)은 codegen 없이 jOOQ DSL(이름 기반 `DSL.table`/`DSL.field`)로 조회. 공휴일은 V1이 만든 `holiday` 테이블을 `V3__drop_holiday_table.sql`로 제거하고 인메모리(`InMemoryHolidayRepository`)로 이관했다 — 적용된 V1은 불변이라 별도 마이그레이션으로 DROP하고, codegen은 V1만 읽으므로 `excludes="HOLIDAY"`(build.gradle.kts)로 생성 타입에서 뺀다. 구체 구현은 `apps/batch — adapter/out` 섹션(`NotificationLogStoreAdapter`, `InMemoryHolidayRepository`, `read/*`) 및 `JooqConfig` 참조. 스키마 SSOT는 `apps/batch/src/main/resources/db/migration/`(V1 앱 테이블 + V3 holiday drop).

## modules/fcm — FCM 기술 인프라

- `modules/fcm/.../infra/fcm/FirebaseConfig.kt:15` (`class FirebaseConfig`, `@Configuration :13`) — Firebase Admin SDK 초기화(batch-design §4, 기술 인프라 모듈). `neki.fcm.enabled=true`일 때만 자격증명(`neki.fcm.credentials-location`)을 읽어 `FirebaseApp`(`:18`)과 `FirebaseMessaging`(`:28`) 빈을 등록. "어떤 포트를 구현할지"는 앱(adapter/out) 책임이고, 이 모듈은 FCM 클라이언트 구성만 담당한다.

## modules/scheduling — 스케줄링 기술 인프라

- `modules/scheduling/.../infra/scheduling/SchedulingConfiguration.kt:12` (`class SchedulingConfiguration`, `@Configuration :10`) — 스케줄링 기술 인프라(도메인 무관, 재사용 가능 모듈). `@EnableScheduling`으로 `@Scheduled` 인프라를 켜고 운영 타임존 기준 `Clock` 제공. "무엇을 언제 돌릴지"(cron·활성화 토글)는 이 모듈이 아니라 각 앱의 adapter/in 정책. 그래서 앱 전용 속성(`neki.batch.*`)에 의존하지 않고 일반 속성 `scheduling.zone`만 사용한다.
  - `:14` (`clock`) — 운영 타임존 기준 시계. 기본 Asia/Seoul, `scheduling.zone`으로 재정의 가능.

## apps/batch — adapter/in (인바운드)

- `apps/batch/.../batch/adapter/in/scheduler/NotificationJobScheduler.kt` (`class NotificationJobScheduler`) — cron 스케줄 → Job 기동(batch-design §2/§P7). WEEKLY_REMINDER 매일 20:00 / WEEKEND_EXPLORE 금·토·일 / HOLIDAY_EXPLORE 매일 깨워 발송일 판정(공휴일 아니면 Job 내부 0건). `scheduling-enabled=true`일 때만 활성. 실제 기동(파라미터·중복 방어)은 `NotificationJobLauncher`에 위임 — "언제 어떤 유형"(cron↔유형)만 담당.
- `apps/batch/.../batch/adapter/in/NotificationJobLauncher.kt` (`class NotificationJobLauncher`) — 알림 Job 기동 공용 서비스(스케줄러·테스트 API 공유). `businessDate`(운영 타임존 오늘)·`launchedAt`(ms) 파라미터 구성, 이름→Job 매핑(`launchByName`). **중복/동시 기동 방어(C-1)**: `launchedAt` 유일화로 Spring Batch 중복 방지에 못 기대므로 `JobExplorer.findRunningJobExecutions`로 실행 중이면 건너뜀(null). 단일 JVM 인스턴스 전제(k8s replica=1); 다중 인스턴스는 ShedLock 등 분산 락 필요. **위치 주의**: Spring Batch 결합 코드라 application이 아니라 `adapter/in`에 둔다(ArchUnit `springBatchOnlyInAdapterIn`이 강제).
- `apps/batch/.../batch/adapter/in/web/TestNotificationController.kt` (`class TestNotificationController`) — 테스트용 수동 트리거 API(pod 내부 전용). `POST /test/notifications/jobs/{jobName}`(→ launcher, 202/400/409)·`POST /test/notifications/push`(단건 발송 스모크). `@ConditionalOnProperty(neki.test-api.enabled)`로 기본 비활성 — 인증 없으니 k8s Service/Ingress 노출 금지.
- `apps/batch/.../batch/adapter/in/HolidayLoader.kt` (`class HolidayLoader`) — 기동 완료 이벤트(`@EventListener(ApplicationReadyEvent)`)에 공휴일 CSV를 인메모리로 적재하는 트리거. `neki.batch.holiday-sync-enabled=true`일 때만 활성(운영 한정). 적재 로직은 `HolidaySyncService.sync()`에 위임. CSV는 배포 아티팩트라 "기동 시 적재"로 충분. Google Sheet(issue #17)로 가면 재적재가 필요해 `@Scheduled` cron으로 전환.

## apps/batch — adapter/out (포트 구현 어댑터)

- `apps/batch/.../batch/adapter/out/InMemoryHolidayRepository.kt` (`class InMemoryHolidayRepository`) — `HolidayCalendar`(읽기) + `HolidayStore`(쓰기)를 함께 구현하는 인메모리 저장소(batch-design §P5). 공휴일은 소량·저빈도 변경이라 DB 테이블 대신 프로세스 메모리에 든다. `@Volatile` 불변 스냅샷(`Map<date, Holiday>`)을 통째 교체(copy-on-write)해 적재(HolidayLoader, 단일 이벤트 스레드)와 조회(배치 스레드) 간 가시성을 보장. `upsertAll`=holiday_date 기준 멱등 병합, `holidayToNotifyOn`=스냅샷 전수에서 `notifyDate(=date+notifyOffsetDays)==businessDate` 선택, `clear()`=테스트 격리용. DB 시절 `SCAN_WINDOW`(±3 조회 한계)가 사라져 오프셋이 커도 발송일이 일치하면 매칭되며, `MAX_OFFSET_DAYS(±2)` 초과는 조회를 막지 않고 WARN만 남긴다(데이터 위생).
- `apps/batch/.../batch/adapter/out/CsvHolidaySource.kt` (`class CsvHolidaySource`) — `HolidaySource`의 CSV 구현. 클래스패스 `holidays.csv`(`neki.batch.holiday-csv`로 경로 주입)에서 `holiday_date,name,notify_offset_days` 파싱. 주석(`#`)·빈 줄·헤더 건너뜀, offset 생략 시 0. 추후 Google Sheet 어댑터로 교체(issue #17).
- `apps/batch/.../batch/adapter/out/NotificationLogStoreAdapter.kt:14` (`class NotificationLogStoreAdapter`) — `NotificationLogStore`의 **jOOQ** 구현(batch-design §4 persistence-write). 소유 테이블이라 생성된 타입(`Tables.NOTIFICATION_LOG`)으로 `insertInto`/`fetchExists`. `sentAt`이 비어 있으면 적재 시각 주입(`OffsetDateTime`/UTC). 동일 키 동시 적재는 DB unique 제약이 최종 방어선. `clock`은 기본값 없이 빈 주입 강제(B-4): 스케줄링 모듈이 제공하는 단일 `Clock`(Asia/Seoul) 빈을 일관되게 쓴다.
- `apps/batch/.../batch/config/JooqConfig.kt` (`class JooqConfig`) — jOOQ 렌더링 설정(jOOQ 마이그레이션). codegen이 DDLDatabase로 식별자를 **대문자**로 생성하므로, 런타임엔 PostgreSQL 소문자 폴딩에 맞춰 `RenderNameCase.LOWER` + `RenderQuotedNames.NEVER`(+`renderSchema=false`)로 렌더한다. 안 그러면 `"NOTIFICATION_LOG"."USER_ID"` 인용 대문자로 나가 실제 소문자 컬럼과 어긋남. `Settings` 빈이 아니라 Spring Boot의 `DefaultConfigurationCustomizer`로 적용(전자는 자동구성에 안 먹힘).
- `apps/batch/.../batch/adapter/out/fcm/FcmPushSender.kt:15` (`class FcmPushSender`) — `PushSender`의 Firebase Admin SDK 구현(batch-design §4). `neki.fcm.enabled=true`일 때만 활성(미설정/로컬은 LoggingPushSender가 대체). 토큰 단위 전송 실패는 배치를 중단시키지 않도록 `FcmResult.FAILED`로 흡수. `init` 로그(H-1): 실제 발송 모드임을 기동 로그로 명시해 LoggingPushSender(무발송)와 운영 상태를 구분.
- `apps/batch/.../batch/adapter/out/fcm/LoggingPushSender.kt:12` (`class LoggingPushSender`) — FCM 비활성(`neki.fcm.enabled`!=true) 환경용(로컬/테스트/드라이런). 실제 발송 없이 로그만 남기고 `FcmResult.SKIPPED` 반환. 컨텍스트가 항상 PushSender 빈을 갖도록 보장해 자격증명 없이도 앱 기동·배치 실행 가능. `init` 로그(H-1): 무발송 모드를 기동 시점에 WARN으로 크게 알려 운영에서 "전 건 SKIPPED"가 조용히 지나가지 않게 한다.
- `apps/batch/.../batch/adapter/out/read/HolidayExploreTargetReader.kt:11` (`class HolidayExploreTargetReader`) — HOLIDAY_EXPLORE 발송 대상 리더(shared-db §1/§5: 최근 1달 사진 업로드 이력 + 푸시동의). 지도사용 이력 데이터 부재(이슈 #292)로 업로드 이력만으로 대상 축소. `[공휴일명]`은 유저별이 아니라 실행(발송일) 단위 값이라 호출자가 주입한 `holidayName`을 모든 대상에 동일하게 채운다.
- `apps/batch/.../batch/adapter/out/read/WeekendExploreTargetReader.kt` (`class WeekendExploreTargetReader`) — WEEKEND_EXPLORE 발송 대상 리더(shared-db §1: 전체 대상자 + 푸시동의). 대상 = 동의자 전원, 변수 없음. 공통 골격 `TargetReaderSupport.query`에 위임(추가 조건 없음).
- `apps/batch/.../batch/adapter/out/read/TargetReaderSupport.kt` (`object TargetReaderSupport`) — 세 TargetReader가 공유하는 **jOOQ 조회 골격**(B-1). 외부 소유 테이블(`tb_notification`/`tb_photo_image`)은 codegen 대상이 아니라 jOOQ 이름 기반 참조(`DSL.table`/`DSL.field`)로 표현한다(plain SQL 문자열 대신 jOOQ DSL로 조립 → 바인딩·JooqConfig 렌더가 일관 적용). `query(dsl, after, size, extraColumns, extraCondition)`가 **푸시 동의(단일 출처)·keyset 커서·정렬·LIMIT를 강제**하므로 각 리더는 고유 SELECT 컬럼·WHERE 조건만 얹는다 — 동의 필터를 구조적으로 못 빠뜨린다(동의 규칙 단일 출처 가드).
- `apps/batch/.../batch/adapter/out/read/WeeklyReminderTargetReader.kt` (`class WeeklyReminderTargetReader`) — WEEKLY_REMINDER 발송 대상 리더(shared-db §1: 7일 전 사진 업로드 동의자). jOOQ EXISTS로 `businessDate-7일` 업로드 존재를 판정하고 상관 서브쿼리로 `[최근 업로드 요일]`을 채운다. soft-delete(`deleted_at IS NULL`) 사진은 제외.
- `apps/batch/.../batch/adapter/out/read/KoreanWeekday.kt` (`object KoreanWeekday`, `:18 recentUploadLabel`) — `[최근 업로드 요일]` 변수 포맷(copy-spec §3 예시 "지난 토요일"). 가정: 최근 업로드 일자의 요일에 "지난 " 접두사. 정확한 카피 톤은 추후 조정 가능하도록 이 한 곳에 격리.

## apps/batch — application (유스케이스)

- `apps/batch/.../batch/application/HolidaySyncService.kt` (`class HolidaySyncService`) — 공휴일 원천(`HolidaySource`) → 인메모리 저장소(`HolidayStore`) 적재 유스케이스. 원천이 CSV든 Sheet든, 저장소가 DB든 인메모리든 서비스는 불변 — 어댑터만 교체(헥사고날). `sync()`는 적재 건수 반환.
- `apps/batch/.../batch/application/NotificationSendService.kt` (`class NotificationSendService`) — 알림 **1건 처리** 유스케이스(batch-design §5). Spring Batch와 무관하게 "1건을 어떻게 준비/발송하는가"만 담당한다. `prepare(target, type, businessDate)` = 당일 중복 조회(`NotificationLogStore.alreadySent`) + 도메인 판정(`NotificationProcessor.decide`) → `PreparedNotification?`(Skip이면 null). `dispatch(prepared)` = `PushSender.send` 후 결과로 `NotificationLogStore.save`. "여러 건을 어떤 단위로 읽고 커밋하는가"(페이징·청크·**트랜잭션 경계**)는 이 서비스가 아니라 배치 어댑터(adapter/in/batch, `CHUNK_SIZE=1`)의 책임이므로, `dispatch`의 send-then-save dual-write 롤백 위험(B-5/M-3)은 호출자 청크가 1건으로 한정한다. 배치 외 트리거(예: 어드민 단건 발송)에서도 재사용 가능.

## apps/batch — adapter/in/batch (배치 구동 어댑터)

Spring Batch(구동/딜리버리 메커니즘)에 결합된 인바운드 어댑터. Job 조립·청크·**트랜잭션 경계**만 여기 있고, "1건을 어떻게 처리하나"의 실질 로직은 `application/NotificationSendService`로 위임한다. (구 `application/job`·`application/step`에서 이관 — 프레임워크 코드가 application 계층에 있던 것을 바로잡음.) 청크 스텝 기계(Factory + Reader/Processor/Writer)는 항상 함께 쓰여 `NotificationStepFactory.kt` 한 파일에 모았고, 세 컴포넌트는 팩토리만 생성하는 구현 세부라 `private`이다.

- `apps/batch/.../batch/adapter/in/batch/NotificationStepFactory.kt:29` (`class NotificationStepFactory`, `@Component :28`) — 알림 배치 Job 3종의 공통 골격 조립(batch-design §5). 세 Job(WEEKEND/WEEKLY/HOLIDAY)은 Reader → Processor → Writer 청크 구조가 동일하고 Reader 쿼리와 `NotificationType`만 다르다. 동일한 청크/Job 조립 보일러플레이트를 한 곳에 모아 타입별 `~Job` 클래스는 자기 Reader·타입만 선언하게 한다(OCP: 타입 추가 = 클래스 추가). 실질 로직은 `NotificationSendService`(생성자 주입)에 위임 — 팩토리는 청크 경계·트랜잭션·배선만 담당. Writer는 무상태 싱글턴이라 팩토리가 직접 보유.
  - `pagingReader(reader: SendTargetReader)` — `SendTargetReader` 포트를 소비하는 keyset 페이징 Reader 생성. Job이 유형별 리더를 SAM 람다로 바인딩해 넘긴다.
  - `processor(type, businessDate)` — `NotificationSendService.prepare`에 위임하는 Processor 생성.
  - `chunkStep` — Reader → Processor → (공통)Writer 청크 Step.
  - `singleStepJob` — 단일 Step Job.
  - 동거 `private` 컴포넌트(같은 파일, 팩토리 전용): `PagingSendTargetItemReader`(`SendTargetReader` 포트에서 `user_id` 오름차순 keyset 페이징, 빈 페이지=소진, 단일 인스턴스·단일 스레드 전제), `NotificationItemProcessor`(`NotificationSendService.prepare`에 위임, Skip이면 null로 청크 필터), `NotificationItemWriter`(청크의 각 건을 `NotificationSendService.dispatch`로 흘려보냄).
  - `CHUNK_SIZE = 1` (B-5/M-3) — 발송(FCM, 비트랜잭션 외부 부수효과)과 이력 적재(트랜잭션)를 **건별로 커밋**한다. 청크>1이면 한 건의 `save()` 실패가 같은 청크의 이미 발송된 다른 건들의 적재까지 롤백시켜, 재실행 시 그만큼 중복 발송된다(`prepare`의 `alreadySent`가 커밋된 이력만 보기 때문). 청크=1은 그 블라스트 반경을 "실패한 그 1건"으로 한정한다. 잔여 윈도우: 그 1건은 발송 후 `save()`가 실패하면 재실행 시 1회 중복 가능(단일 인스턴스·`FcmPushSender`가 예외를 흡수해 청크 중단은 `save()` 실패에 한정되므로 드묾). 완전 at-most-once가 필요해지면(예: 다중 인스턴스화) 발송 전 별도 트랜잭션 멱등 클레임(unique 제약 선점) 패턴으로 강화. `PAGE_SIZE = 100`은 DB 조회 효율을 위해 유지(조회 단위 ≠ 커밋 단위).
- `apps/batch/.../batch/adapter/in/batch/WeekendExploreJob.kt:18` (`class WeekendExploreJob`, `@Configuration("weekendExploreJobConfig") :17`) — WEEKEND_EXPLORE Job(batch-design §5): 동의자 전원에게 주말 탐방 알림. 대상 = `push_agreed=true` 전원, 변수 없음. `WeekendExploreTargetReader`를 `SendTargetReader`로 바인딩. 골격 조립은 NotificationStepFactory에 위임.
- `apps/batch/.../batch/adapter/in/batch/WeeklyReminderJob.kt:18` (`class WeeklyReminderJob`, `@Configuration("weeklyReminderJobConfig") :17`) — WEEKLY_REMINDER Job(batch-design §5): 7일 전 업로드 동의자에게 주간 리마인드. Reader가 `businessDate`(JobParameter)를 받아 7일 전 업로드 이력을 조회하고 `[최근 업로드 요일]`을 채운다. 골격 조립은 NotificationStepFactory에 위임.
- `apps/batch/.../batch/adapter/in/batch/HolidayExploreJob.kt:19` (`class HolidayExploreJob`, `@Configuration("holidayExploreJobConfig") :18`) — HOLIDAY_EXPLORE Job(batch-design §P5): 공휴일 발송일에 최근 1달 업로드 동의자에게 알림. 매일 깨어나 `HolidayCalendar`로 발송일 여부를 판정. 발송일이 아니면 빈 Reader로 0건 처리, 발송일이면 `[공휴일명]`을 채워 발송. 골격 조립은 NotificationStepFactory에 위임.
  - `holidayItemReader` — 발송일이 아니면(공휴일 매칭 없음) 아무 것도 읽지 않는다(빈 `SendTargetReader` 반환).

> B-3(컴포넌트 스캔 이중화 ArchUnit 가드)는 jOOQ 마이그레이션으로 JPA 스캔(`@EntityScan`/`@EnableJpaRepositories`)이 사라져 더 이상 해당 사항이 없으므로 제거됨.

# 알림 정책 (Notification Policy)

본 문서는 **무엇을·누구에게·언제·어떤 규칙으로 보내는가**(정책)를 규정한다.
구현 방법(how)은 `docs/lld/`를 참조한다. 문구/톤/변수 세부 규칙은 [notification-copy-spec.md](notification-copy-spec.md)가 SSOT다.

기준 코드 스냅샷: 2026-07 (jOOQ 전환 후, ADR 0001 반영).

## 1. 목적

네키 사용자에게 **시간 기반 대량 푸시 알림**을 스케줄로 발송한다. 푸시(마케팅) 수신 동의 사용자를 공통 대상으로 하며, 가능하면 개인화 변수를 문구에 적용한다.

## 2. 알림 카탈로그 (in-scope 3종)

| 알림 (`NotificationType`) | 트리거(cron) | 발송 대상 | 개인화 변수 |
| --- | --- | --- | --- |
| **아카이빙 1주일 리마인드** (`WEEKLY_REMINDER`) | 매일 20:00 | `businessDate - 7일`에 사진 업로드 이력 + 푸시동의 | `[최근 업로드 요일]` |
| **주말 전 탐색** (`WEEKEND_EXPLORE`) | 금·토·일 18:00 | 푸시동의 전원 | 없음 |
| **연휴/공휴일 탐색** (`HOLIDAY_EXPLORE`) | 매일 09:00 (발송일 판정) | 최근 1달 사진 업로드 이력 + 푸시동의 | `[공휴일명]` |

- cron 값은 프로퍼티(`neki.batch.cron.*`)로 외부화되어 있다. 현행값은 `docs/lld/scheduling-and-config.md` 참조.
- `HOLIDAY_EXPLORE`는 매일 깨어나 오늘이 어떤 공휴일의 발송일인지 판정한다. 발송일이 아니면 0건 처리한다(잡은 매일 돌지만 대부분의 날은 발송 없음).

### Out of Scope

- 길찾기 후 1시간 아카이빙 유도 알림 (스펙 아웃)
- 브랜드 업데이트 알림 (별도 API 직접 호출로 처리)
- 클릭 이벤트 수집 (클라이언트/별도 시스템 책임). 본 앱은 발송 이력 + 메타데이터만 적재.
- 외부 공휴일 API(특일정보) 연동 — 내부 `holiday` 테이블 시드로 대체.

## 3. 발송 대상 조건 (Targeting)

공통 전제: **푸시(마케팅) 수신 동의 사용자**.

| 알림 | 대상 조건 |
| --- | --- |
| WEEKLY_REMINDER | `push_agreed = true` **AND** `businessDate - 7일` 당일에 업로드 이력 존재 |
| WEEKEND_EXPLORE | `push_agreed = true` 전원 |
| HOLIDAY_EXPLORE | `push_agreed = true` **AND** 최근 1달 내 업로드 이력 존재(EXISTS) |

## 4. 수신 동의 정책 (Consent) — ⚠️ 구현 현실

> **현행 구현의 단일 동의 출처는 `tb_notification.push_agreed = true` 컬럼이다.**
> 모든 TargetReader가 공통 술어 `n.push_agreed = true`로 동의를 필터링하며(`TargetReaderSupport.PAGING_PREDICATE`), 동의 판정은 **읽기 쿼리에서 한 번만** 이뤄진다. 도메인 `NotificationProcessor`는 동의를 재확인하지 않고 중복 발송만 판정한다.

역사적 설계(`docs/superpowers/specs/2026-06-10-shared-db-data-source-and-dependencies.md`)는 `TB_USER_TERM_AGREEMENT.withdrawn_at IS NULL`(마케팅 약관 동의) 기반 판정을 계획했으나, **현재 코드는 그 조인을 사용하지 않고** `tb_notification.push_agreed`로 단순화되어 있다. 약관 기반 동의로 전환하려면 리더 쿼리 변경이 필요하며, 그때 이 문서와 스키마 의존성을 함께 갱신한다.

## 5. 개인화 변수 / 문구 정책

- 변수·톤·문구 테이블·치환/폴백 규칙의 SSOT는 [notification-copy-spec.md](notification-copy-spec.md).
- 요약: `[최근 업로드 요일]`(WEEKLY_REMINDER), `[공휴일명]`(HOLIDAY_EXPLORE). WEEKEND_EXPLORE는 변수 없음.
- **변수값이 없으면(blank/null)** 해당 알림의 기본(폴백) 톤 템플릿으로 렌더링한다(개인화 생략, 발송 자체는 진행).

## 6. 톤 A/B 배정 정책

톤별 클릭률 분석(스펙 1-6)을 위해 유저별로 톤을 **결정적으로** 배정한다.

- 규칙: `tone = MessageTone.entries[ Math.floorMod(userId, 3) ]` → `0=INFORMATIVE, 1=FRIENDLY, 2=SUGGESTIVE`
- 같은 유저는 항상 같은 톤 코호트에 속한다(안정적 A/B 버킷). 배정된 톤은 `notification_log.message_tone`에 기록한다.

## 7. 중복 발송 방지 / 전달 보장 정책

- **중복 키**: `(user_id, notification_type, business_date)`. 같은 유저에게 같은 알림 타입을 같은 논리적 날짜(`businessDate`)에 두 번 보내지 않는다.
- **이중 방어**:
  1. 도메인 판정 — Processor가 발송 전 `alreadySent(userId, type, businessDate)`를 조회해 이미 보냈으면 스킵.
  2. DB 제약 — `notification_log`의 `UNIQUE(user_id, notification_type, business_date)`가 최종 방어선.
- **전달 보장 수준**: **at-least-once**. 발송(FCM, 트랜잭션 밖)과 이력 적재(DB)가 dual-write이므로, "발송 성공 후 적재 실패" 순간에 재실행되면 그 1건이 중복될 수 있다. 이 잔여 창은 `CHUNK_SIZE=1`(건별 커밋)로 최대 1건으로 한정된다. 상세: `docs/lld/batch-pipeline.md`.
- **rollout(재배포) 시 재전송**: 스케줄이 cron 기반이라 재기동만으로는 잡이 다시 뜨지 않으며, 같은 날 재실행되어도 `alreadySent`로 스킵된다. 유일한 위험은 "발송 도중 강제 종료 + 당일 재실행"이 겹칠 때의 최대 1건.

## 8. 운영 전제

- **단일 인스턴스 운영**(k8s replica=1). 중복/동시 기동 방어(`JobExplorer.findRunningJobExecutions`)는 단일 JVM 내에서만 유효하다. 다중 인스턴스로 확장하려면 ShedLock 등 분산 락 + 발송 전 멱등 클레임이 필요하다.
- 발송 대상 조회는 백엔드 공유 PostgreSQL(`Team-Neki-Server`)의 외부 소유 테이블에 **read-only**로 의존한다. 외부 스키마 의존성은 `docs/lld/data-access.md` 참조.

## 9. 외부 의존성 상태

| 항목 | 상태 | 정책 영향 |
| --- | --- | --- |
| FCM 토큰 소스 | 현재 리더가 `tb_notification.device_token` **컬럼**을 가정 | 백엔드가 별도 토큰 테이블([#291](https://github.com/Team-Neki/Team-Neki-Server/issues/291))로 구현하면 쿼리 불일치 → 머지 전 정합성 확인 필요 |
| 지도/길찾기 이력 | 데이터 소스 부재([#292](https://github.com/Team-Neki/Team-Neki-Server/issues/292)) | HOLIDAY_EXPLORE 대상을 "업로드 이력"만으로 축소 |
| 공휴일 원천 | CSV 번들(`CsvHolidaySource`) | 추후 Google Sheet 전환([#17]) |

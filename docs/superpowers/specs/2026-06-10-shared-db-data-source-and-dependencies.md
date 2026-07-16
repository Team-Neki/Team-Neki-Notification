# 공유 DB 데이터 소스 매핑 및 외부 의존성

작성일: 2026-06-10
대상 백엔드: `Team-Neki-Server` (https://github.com/Team-Neki/Team-Neki-Server) — 공유 PostgreSQL, Flyway V1~V20 기준

## 1. 알림별 발송 대상 조회 — 확정 데이터 소스

공통 조건: **푸시 수신 동의 사용자** (`TB_NOTIFICATION.push_agreed = true`).

업로드 이력을 보는 알림은 **소프트 삭제된 사진을 제외**한다(`TB_PHOTO_IMAGE.deleted_at IS NULL`).
사용자가 지운 사진은 업로드 이력으로 세지 않는다 — 삭제한 사진을 근거로 알림이 가면 안 되기 때문.

| 알림 | 대상 조건 | 데이터 소스 | 상태 |
| --- | --- | --- | --- |
| WEEKLY_REMINDER | 7일 전 사진 업로드 이력(삭제 제외) + 푸시동의 | `TB_PHOTO_IMAGE.created_at` + `.deleted_at IS NULL`, `TB_NOTIFICATION.push_agreed` | ✅ 가능 |
| WEEKEND_EXPLORE | 전체 대상자 + 푸시동의 | `TB_USERS`, `TB_NOTIFICATION.push_agreed` | ✅ 가능 |
| HOLIDAY_EXPLORE | 최근 1달 (지도사용 OR 업로드, 삭제 제외) + 푸시동의 | 업로드: `TB_PHOTO_IMAGE.created_at` + `.deleted_at IS NULL` / 지도사용: **없음** / 동의: `TB_NOTIFICATION.push_agreed` | ⚠️ 업로드만으로 축소 (이슈 #292) |

변수 원천:
- `[최근 업로드 요일]` ← `TB_PHOTO_IMAGE.created_at`의 요일 (미삭제 사진 중 `MAX(created_at)`). `captured_at`은 쓰지 않는다 — 알림 문구가 "언제 올렸는지"를 말하므로 촬영 시각이 아니라 업로드 시각이 기준.
- `[공휴일명]` ← 인메모리 `InMemoryHolidayRepository` (기동 시 CSV 적재). **소유 테이블 없음** — §5 참조.

## 2. 푸시 수신 동의 판정 — 확정 모델 (2026-06-18 갱신)

발송 대상의 푸시 동의는 **`TB_NOTIFICATION.push_agreed`** 단독으로 판정한다.
토큰(`device_token`)과 동의 플래그(`push_agreed`)가 한 행에 함께 있어 조회·필터가 join 하나로 끝난다.

| 컬럼 | 의미 |
| --- | --- |
| `user_id` (unique, not null) | 유저당 1행 |
| `device_token` (not null, 512) | FCM 발송 토큰 |
| `push_agreed` (not null, default false) | **true = 발송 대상** |

**동의자 판정 쿼리(개념):**
```sql
SELECT user_id, device_token
FROM TB_NOTIFICATION
WHERE push_agreed = true
```

### 2-1. (백로그) 마케팅 약관 동의 연계

마케팅 **약관** 동의(`TB_USER_TERM_AGREEMENT` MARKETING, PR #290 V19/V20)는 본 스프린트 발송 필터에서 **사용하지 않는다 — 백로그**.
근거: 약관 동의(법적)와 푸시 수신 동의(알림 on/off)는 별개 개념이며, 본 앱 발송은 후자(`push_agreed`)만 본다.
향후 "마케팅 약관 동의 AND 푸시 동의" 같은 이중 게이트가 필요해지면 아래 모델을 되살린다.

<details><summary>보류된 약관-동의 판정 모델 (참고용)</summary>

근거: `Team-Neki-Server` PR #290 (마케팅 수신 동의 약관 추가), 마이그레이션 V19/V20.
- V19: `TB_TERM`에 `term_type='MARKETING'`(`id=4`, `is_required=false`, `is_active=true`) 추가.
- V20: `TB_USER_TERM_AGREEMENT.withdrawn_at TIMESTAMP NULL` 추가. **NULL = 현재 동의 / 非NULL = 철회.**

```sql
SELECT uta.user_id
FROM TB_USER_TERM_AGREEMENT uta
JOIN TB_TERM t ON t.id = uta.term_id
WHERE t.term_type = 'MARKETING'
  AND t.is_active = true
  AND uta.withdrawn_at IS NULL
```
- `(user_id, term_id)` 복합 PK라 유저당 행 1개 → 최신 상태만 판정(이력은 향후 `TB_USER_TERM_AGREEMENT_HIST` 도입 예정).
</details>

## 3. 관련 공유 테이블 (읽기 전용)

- `TB_USERS(id, ...)` — 유저 식별
- `TB_PHOTO_IMAGE(user_id, media_id, memo, created_at, updated_at, upload_method[QR/DIRECT_UPLOAD], captured_at, deleted_at)` — 업로드 이력/요일 (V18에서 folder_id 제거됨). `deleted_at`은 **소프트 삭제**(Server #298) — 대상 조회는 `deleted_at IS NULL`만 센다(§1).
- `TB_NOTIFICATION(id, user_id[unique,not null], device_token[not null,512], push_agreed[not null,default false], created_at, updated_at)` — **FCM 토큰 + 푸시 동의** (이슈 #291 확정)
- `TB_TERM`, `TB_USER_TERM_AGREEMENT(...)` — 마케팅 약관 동의. **백로그(§2-1), 현재 미사용**

## 4. 외부 의존성 / 블로커

| # | 항목 | 상태 | 영향 |
| --- | --- | --- | --- |
| PR #290 | 마케팅 동의 약관(V19/V20) | **백로그** | 발송 필터가 `push_agreed`로 전환되어 더 이상 의존하지 않음(§2-1). 이중 게이트 필요 시 재검토 |
| 이슈 #291 | 디바이스(FCM) 토큰 테이블 | **확정 (`TB_NOTIFICATION`)** | 해소. `user_id`/`device_token`/`push_agreed` 보유 → 토큰 조회·동의 필터 모두 단일 테이블에서 해결 |
| 이슈 #292 | 지도/길찾기 사용 이력 | 보류 | HOLIDAY_EXPLORE 대상을 업로드 이력만으로 축소. 데이터 생기면 확장 |

## 5. 이번 스프린트 적용 결정

- HOLIDAY_EXPLORE 대상 = **최근 1달 사진 업로드 이력(삭제 제외) + `push_agreed`** (지도사용 절반 제외).
- 푸시 동의·FCM 토큰 모두 `TB_NOTIFICATION` 단일 테이블(#291 확정)에서 조회. 대상 쿼리는 **`tb_notification`을 베이스 테이블**로 두고 `push_agreed = true` + keyset 커서(`user_id > ?`)를 걸며, 업로드 이력 조건은 `tb_photo_image` **EXISTS 서브쿼리**로 얹는다. 이 골격(동의·커서·정렬·LIMIT)은 `TargetReaderSupport.query()`가 강제해 리더가 동의 필터를 빠뜨릴 수 없다.
- 마케팅 약관 동의(#290) 연계는 **백로그**(§2-1). 이번 스프린트 발송 필터에서 제외.
- 우리 소유 테이블은 `notification_log` + Spring Batch `BATCH_*`뿐이며 **별도 Flyway 마이그레이션**으로 추가하되, 공유 DB Flyway 이력과 충돌하지 않도록 네이밍/실행 주체를 백엔드와 협의(예: 별도 schema 또는 분리된 마이그레이션 경로).
- **공휴일은 소유 테이블이 없다.** V1이 만든 `holiday` 테이블은 `V3__drop_holiday_table.sql`로 제거하고 `InMemoryHolidayRepository`로 이관했다(기동 시 CSV 적재). 상세는 [holiday-sync.md](../../lld/holiday-sync.md).

## 6. Flyway 소유권 주의

공유 DB의 Flyway 이력(`flyway_schema_history`)은 `Team-Neki-Server`가 소유. 알림 앱이 같은 이력 테이블에 마이그레이션을 끼워넣으면 충돌 위험. → 알림 앱 전용 테이블은 **별도 history 테이블(`flyway.table`) 또는 별도 schema**로 분리하는 방안을 P4 전에 백엔드와 확정한다.

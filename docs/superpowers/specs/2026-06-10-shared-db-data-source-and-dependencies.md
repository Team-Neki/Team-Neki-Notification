# 공유 DB 데이터 소스 매핑 및 외부 의존성

작성일: 2026-06-10
대상 백엔드: `Team-Neki-Server` (https://github.com/Team-Neki/Team-Neki-Server) — 공유 PostgreSQL, Flyway V1~V20 기준

## 1. 알림별 발송 대상 조회 — 확정 데이터 소스

공통 조건: **푸시(마케팅) 수신 동의 사용자**.

| 알림 | 대상 조건 | 데이터 소스 | 상태 |
| --- | --- | --- | --- |
| WEEKLY_REMINDER | 7일 전 사진 업로드 이력 + 푸시동의 | `TB_PHOTO_IMAGE.created_at`, 마케팅 동의 | ✅ 가능 |
| WEEKEND_EXPLORE | 전체 대상자 + 푸시동의 | `TB_USERS`, 마케팅 동의 | ✅ 가능 |
| HOLIDAY_EXPLORE | 최근 1달 (지도사용 OR 업로드) + 푸시동의 | 업로드: `TB_PHOTO_IMAGE.created_at` / 지도사용: **없음** | ⚠️ 업로드만으로 축소 (이슈 #292) |

변수 원천:
- `[최근 업로드 요일]` ← `TB_PHOTO_IMAGE.created_at`(또는 `captured_at`)의 요일
- `[공휴일명]` ← 우리 소유 `holiday` 테이블 (P5)

## 2. 푸시(마케팅) 수신 동의 판정 — 확정 모델

근거: `Team-Neki-Server` PR #290 (마케팅 수신 동의 약관 추가), 마이그레이션 V19/V20.

- V19: `TB_TERM`에 `term_type='MARKETING'`(`id=4`, `is_required=false`, `is_active=true`) 추가.
- V20: `TB_USER_TERM_AGREEMENT.withdrawn_at TIMESTAMP NULL` 추가. **NULL = 현재 동의 / 非NULL = 철회.**

**동의자 판정 쿼리(개념):**
```sql
SELECT uta.user_id
FROM TB_USER_TERM_AGREEMENT uta
JOIN TB_TERM t ON t.id = uta.term_id
WHERE t.term_type = 'MARKETING'   -- 하드코딩 id=4 대신 term_type로 조인(견고성)
  AND t.is_active = true
  AND uta.withdrawn_at IS NULL
```
- `(user_id, term_id)` 복합 PK라 유저당 행 1개 → 최신 상태만 판정 가능(이력은 향후 `TB_USER_TERM_AGREEMENT_HIST` 도입 예정, PR #290 Notes 참고).

## 3. 관련 공유 테이블 (읽기 전용)

- `TB_USERS(id, ...)` — 유저 식별
- `TB_PHOTO_IMAGE(user_id, media_id, memo, created_at, updated_at, upload_method[QR/DIRECT_UPLOAD], captured_at)` — 업로드 이력/요일 (V18에서 folder_id 제거됨)
- `TB_TERM`, `TB_USER_TERM_AGREEMENT(user_id, term_id, agreed_at, term_version, withdrawn_at)` — 동의 판정
- FCM 토큰: **테이블 없음** (이슈 #291)

## 4. 외부 의존성 / 블로커

| # | 항목 | 상태 | 영향 |
| --- | --- | --- | --- |
| PR #290 | 마케팅 동의 약관(V19/V20) | OPEN(미병합) | 동의 판정이 이 머지에 의존. 머지 전엔 테스트는 Testcontainers에 동일 스키마를 시드해 진행 |
| 이슈 #291 | 디바이스(FCM) 토큰 테이블 | 백엔드 추가 예정 | **발송 자체 블로킹**. P3/P4의 토큰 조회·발송 경로가 대기 |
| 이슈 #292 | 지도/길찾기 사용 이력 | 보류 | HOLIDAY_EXPLORE 대상을 업로드 이력만으로 축소. 데이터 생기면 확장 |

## 5. 이번 스프린트 적용 결정

- HOLIDAY_EXPLORE 대상 = **최근 1달 사진 업로드 이력 + 마케팅 동의** (지도사용 절반 제외).
- FCM 토큰 테이블(#291)이 확정되기 전까지: 발송 대상 조회 어댑터(유저/동의/업로드)는 진행하되, **토큰 조회·실제 FCM 발송 경로는 #291 스키마 확정 후 바인딩**. 그 전에는 포트 인터페이스 + 페이크로 테스트.
- 우리 소유 테이블(`notification_log`, `holiday`, Spring Batch `BATCH_*`)은 **별도 Flyway 마이그레이션**으로 추가하되, 공유 DB Flyway 이력과 충돌하지 않도록 네이밍/실행 주체를 백엔드와 협의(예: 별도 schema 또는 분리된 마이그레이션 경로).

## 6. Flyway 소유권 주의

공유 DB의 Flyway 이력(`flyway_schema_history`)은 `Team-Neki-Server`가 소유. 알림 앱이 같은 이력 테이블에 마이그레이션을 끼워넣으면 충돌 위험. → 알림 앱 전용 테이블은 **별도 history 테이블(`flyway.table`) 또는 별도 schema**로 분리하는 방안을 P4 전에 백엔드와 확정한다.

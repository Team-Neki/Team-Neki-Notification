# 알림 문구 / 변수 사양 (구현 단일 소스)

본 문서는 PRD의 문구·변수 규칙을 구현 관점에서 확정한 **단일 진실 소스(SSOT)** 다.
모든 구현/테스트/검수 에이전트는 이 문서를 기준으로 한다. 범위는 in-scope 알림 3종.

## 1. 알림 타입

| enum `NotificationType` | 의미 | 기본(폴백) 톤 |
| --- | --- | --- |
| `WEEKLY_REMINDER` | 아카이빙 1주일 후 리마인드 | `INFORMATIVE` |
| `WEEKEND_EXPLORE` | 주말 전 포토부스 탐색 | `INFORMATIVE` |
| `HOLIDAY_EXPLORE` | 연휴/공휴일 포토부스 탐색 | `SUGGESTIVE` |

## 2. 톤

| enum `MessageTone` | PRD 명칭 |
| --- | --- |
| `INFORMATIVE` | 정보형 |
| `FRIENDLY` | 친근형 |
| `SUGGESTIVE` | 제안형 |

## 3. 변수

| 변수 토큰 | 의미 | 적용 알림/톤 |
| --- | --- | --- |
| `[최근 업로드 요일]` | 최근 사진 업로드 요일/시점 | `WEEKLY_REMINDER` / `SUGGESTIVE` 제목 |
| `[공휴일명]` | 발송 기준 공휴일/연휴명 | `HOLIDAY_EXPLORE` / `INFORMATIVE`·`FRIENDLY` 제목 |

`WEEKEND_EXPLORE`는 변수 없음(기본 문구 우선).

## 4. 문구 테이블

치환 토큰은 문구 안에 대괄호 그대로 표기한다. `{변수}` 위치에 변수값을 넣는다.

### WEEKLY_REMINDER
| 톤 | 제목 | 본문 | 필요 변수 |
| --- | --- | --- | --- |
| INFORMATIVE | 일주일 전 사진이 있어요 | 네키에 저장한 네컷을 다시 확인해보세요. | 없음 |
| FRIENDLY | 벌써 일주일 전 네컷이에요 | 지난 사진을 네키에서 다시 꺼내보세요. | 없음 |
| SUGGESTIVE | {최근 업로드 요일}처럼 오늘도 남겨볼까요? | 오늘 찍은 사진도 네키에 정리해보세요. | `[최근 업로드 요일]` |

### WEEKEND_EXPLORE
| 톤 | 제목 | 본문 | 필요 변수 |
| --- | --- | --- | --- |
| INFORMATIVE | 주말 전 포토부스 확인하기 | 가까운 포토부스를 네키 지도에서 확인해보세요. | 없음 |
| FRIENDLY | 이번 주말엔 어디서 찍을까요? | 약속 전에 근처 포토부스를 미리 찾아보세요. | 없음 |
| SUGGESTIVE | 약속 전에 미리 찾아보세요 | 가까운 포토부스를 네키 지도에서 확인해보세요. | 없음 |

### HOLIDAY_EXPLORE
| 톤 | 제목 | 본문 | 필요 변수 |
| --- | --- | --- | --- |
| INFORMATIVE | {공휴일명} 포토부스 확인하기 | 쉬는 날 방문할 포토부스를 네키 지도에서 확인해보세요. | `[공휴일명]` |
| FRIENDLY | {공휴일명}에 약속 있으신가요? | 약속 전에 근처 포토부스를 미리 확인해보세요! | `[공휴일명]` |
| SUGGESTIVE | 쉬는 날 가기 좋은 포토부스 | 네키 지도에서 가까운 포토부스를 확인해보세요. | 없음 |

## 5. 톤 배정 정책 (A/B)

스펙 1-6(톤별 클릭률 분석)을 위해 **유저별로 톤을 결정적으로(deterministic) 배정**한다.
같은 유저는 항상 같은 톤 버킷에 속해 코호트가 안정적이어야 한다.

- 규칙: `tone = MessageTone.entries[ floorMod(userId, 3) ]`
  - `floorMod(userId, 3) == 0 → INFORMATIVE`, `1 → FRIENDLY`, `2 → SUGGESTIVE`
  - `MessageTone.entries` 순서는 `INFORMATIVE, FRIENDLY, SUGGESTIVE`로 고정한다(이 순서를 enum 선언 순서로 보장).
- `userId`는 `Long`. 음수 방지를 위해 `Math.floorMod` 사용.

## 6. 변수 치환 / 폴백 규칙 (핵심 결정)

문구 렌더링 입력: `(NotificationType type, MessageTone assignedTone, variables: Map<token,String?>)`
출력: `RenderedMessage(title, body, actualTone, variableApplied)`

규칙(순서대로 적용):

1. `assignedTone`의 템플릿이 **필요 변수**를 갖지 않으면 → 그대로 렌더링. `variableApplied=false`, `actualTone=assignedTone`.
2. `assignedTone`의 템플릿이 필요 변수를 갖고, **그 값이 존재하고 비어있지 않으면** → 변수 치환 후 렌더링. `variableApplied=true`, `actualTone=assignedTone`.
3. `assignedTone`의 템플릿이 필요 변수를 갖는데 **값이 없거나(blank/null)** → 해당 알림의 **기본 톤(§1)** 템플릿으로 폴백한다. `variableApplied=false`, `actualTone=기본 톤`.
   - 기본 톤은 정의상 변수를 필요로 하지 않는다(§1·§4에서 보장).
4. "변수값이 부정확한 경우 개인화하지 않음"(PRD 1-4)은 이번 구현에서 **호출자가 부정확한 값을 애초에 null로 넘긴다**고 가정한다. 즉 렌더러는 blank/null = 미존재로만 처리한다.

빈 문자열·공백·null은 모두 "값 없음"으로 취급한다(`isNullOrBlank`).

## 7. business_date / 중복 방지

- `businessDate`: 발송 실행의 논리적 날짜(`LocalDate`). 호출자가 발송 기준 시각의 날짜를 주입한다.
- 중복 키: `(userId, NotificationType, businessDate)`. 동일 키 재발송 금지(인프라 unique 제약 + 도메인 dedup 판정).
- 도메인 레벨에서는 "이미 발송됨" 여부를 입력으로 받아 발송 대상에서 제외하는 순수 판정 로직만 둔다(이력 조회 자체는 포트 책임).

## 8. P1 도메인 산출물 요약

순수 Kotlin(`domain` 모듈), 프레임워크 의존 없음:
- `NotificationType`, `MessageTone` (enum, 선언 순서 고정)
- `SendTarget` (userId, fcmToken, 변수 원천값 등 — 발송 대상 1건)
- `RenderedMessage` (title, body, actualTone, variableApplied)
- `MessageRenderer` (§4·§6 구현, 순수 함수)
- `ToneAssignmentPolicy` (§5)
- dedup 판정 유틸 (§7)

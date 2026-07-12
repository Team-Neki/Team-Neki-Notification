# LLD — 공휴일 동기화 / 발송일 판정

`HOLIDAY_EXPLORE`는 매일 깨어나 오늘이 어떤 공휴일의 발송일인지 판정한다. 이를 위해 `holiday` 테이블에 공휴일 데이터가 시드되어 있어야 한다.

## 1. 두 개의 분리된 관심사

| 관심사 | 포트 | 구현 | 시점 |
| --- | --- | --- | --- |
| **원천 적재**(seed) | `HolidaySource` → `HolidayStore` | `CsvHolidaySource` → `HolidayStoreAdapter` | 기동 시 1회(`HolidaySyncRunner`) |
| **발송일 판정**(read) | `HolidayCalendar` | `HolidayCalendarAdapter` | 매일 잡 실행 시 |

원천이 CSV든 Sheet든 유스케이스(`HolidaySyncService`)는 불변 — 어댑터만 교체(헥사고날). 추후 Google Sheet 전환 예정([#17]).

## 2. 시드 흐름 (`HolidaySyncService.sync()`)

1. `HolidaySyncRunner`(`neki.batch.holiday-sync-enabled=true`)가 기동 시 트리거.
2. `CsvHolidaySource.load()` — 클래스패스 `holidays.csv`(`neki.batch.holiday-csv`)에서 `holiday_date,name,notify_offset_days` 파싱. 주석(`#`)·빈 줄·헤더 건너뜀, offset 생략 시 0.
3. `HolidayStoreAdapter.upsertAll()` — `onConflict(HOLIDAY_DATE).doUpdate()`로 `holiday_date` 기준 멱등 upsert. `sync()`는 upsert 건수 반환.

> CSV는 배포 아티팩트라 "배포 시 시드"로 충분. 시드가 안 되면 HOLIDAY_EXPLORE는 조용히 0건 발송.

## 3. 발송일 판정 (`HolidayCalendarAdapter.holidayToNotifyOn`)

- 발송일 = `holiday_date + notify_offset_days` (전날=-1, 당일=0).
- `businessDate`가 어떤 공휴일의 발송일이면 그 공휴일(→ `[공휴일명]`)을, 아니면 null을 반환. `HolidayExploreJob`은 null이면 빈 Reader로 0건 처리.
- 오프셋 계산은 Kotlin에서 처리(DB 종속 날짜 연산 회피). businessDate ±`SCAN_WINDOW`일 후보를 조회한 뒤 `notifyDate==businessDate`인 공휴일 선택.

## 4. 오프셋 계약 상수 (`HolidayStoreAdapter`)

| 상수 | 값 | 의미 |
| --- | --- | --- |
| `MAX_OFFSET_DAYS` | `2` | 허용 `|notify_offset_days|` 상한(전날/당일 + 여유). 시드 데이터 계약. |
| `SCAN_WINDOW` | `MAX_OFFSET_DAYS + 1` | 조회 윈도우를 계약보다 1일 넓게 잡아, `|offset|`이 상한을 1 초과(±3)한 데이터도 조용히 누락되지 않고 발송일에 매칭. 그런 후보는 **WARN 로깅**해 상향/점검 유도(윈도우 밖은 여전히 미조회 — 조기 경고 목적). |

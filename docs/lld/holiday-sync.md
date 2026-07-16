# LLD — 공휴일 적재 / 발송일 판정

`HOLIDAY_EXPLORE`는 매일 깨어나 오늘이 어떤 공휴일의 발송일인지 판정한다. 공휴일은 소량·저빈도 변경이라 DB 테이블 대신 **인메모리**(`InMemoryHolidayRepository`)로 관리한다.

## 1. 두 개의 분리된 관심사

| 관심사 | 포트 | 구현 | 시점 |
| --- | --- | --- | --- |
| **원천 적재**(seed) | `HolidaySource` → `HolidayStore` | `CsvHolidaySource` → `InMemoryHolidayRepository` | 기동 완료 이벤트 1회(`HolidayLoader`) |
| **발송일 판정**(read) | `HolidayCalendar` | `InMemoryHolidayRepository` | 매일 잡 실행 시 |

`InMemoryHolidayRepository`가 두 포트(`HolidayStore`+`HolidayCalendar`)를 함께 구현한다. 원천이 CSV든 Sheet든, 저장소가 DB든 인메모리든 유스케이스(`HolidaySyncService`)는 불변 — 어댑터만 교체(헥사고날). 추후 Google Sheet 전환 예정([#17]).

## 2. 적재 흐름 (`HolidaySyncService.sync()`)

1. `HolidayLoader`(`@EventListener(ApplicationReadyEvent)`, `neki.batch.holiday-sync-enabled=true`)가 기동 완료 시 트리거.
2. `CsvHolidaySource.load()` — 클래스패스 `holidays.csv`(`neki.batch.holiday-csv`)에서 `holiday_date,name,notify_offset_days` 파싱. 주석(`#`)·빈 줄·헤더 건너뜀, offset 생략 시 0.
3. `InMemoryHolidayRepository.upsertAll()` — `@Volatile` 스냅샷(`Map<holiday_date, Holiday>`)을 copy-on-write로 교체하며 `holiday_date` 기준 멱등 병합. `sync()`는 적재 건수 반환.

> CSV는 배포 아티팩트라 "기동 시 적재"로 충분. 적재가 안 되면 HOLIDAY_EXPLORE는 조용히 0건 발송. Google Sheet로 가면 재적재가 필요해 `@Scheduled` cron으로 전환.

## 3. 발송일 판정 (`InMemoryHolidayRepository.holidayToNotifyOn`)

- 발송일 = `holiday_date + notify_offset_days` (전날=-1, 당일=0).
- `businessDate`가 어떤 공휴일의 발송일이면 그 공휴일(→ `[공휴일명]`)을, 아니면 null을 반환. `HolidayExploreJob`은 null이면 빈 Reader로 0건 처리.
- 인메모리 전수 스냅샷에서 `notifyDate==businessDate`를 매칭한다. DB 시절의 `SCAN_WINDOW`(±조회 한계)가 없어 오프셋이 커도 발송일이 일치하면 매칭되며, `MAX_OFFSET_DAYS(±2)` 초과 오프셋은 조회를 막지 않고 **WARN만 남긴다**(데이터 위생).

## 4. 스레드 안전성

적재(`HolidayLoader`, 단일 이벤트 스레드)와 조회(배치 스레드)는 `@Volatile` 불변 스냅샷 참조를 통째로 교체(copy-on-write)해 가시성을 보장한다. `clear()`는 테스트 격리용.

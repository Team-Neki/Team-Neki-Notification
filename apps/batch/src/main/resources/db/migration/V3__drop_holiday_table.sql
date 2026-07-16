-- 공휴일을 DB 테이블에서 인메모리(InMemoryHolidayRepository)로 이관하며 holiday 테이블 제거.
-- 원천 CSV는 기동 완료 이벤트에 HolidayLoader가 메모리로 적재하므로 소유 테이블이 필요 없다.
-- (V1이 생성한 테이블을 여기서 정리 — 적용된 V1은 불변이라 별도 마이그레이션으로 DROP)
DROP TABLE IF EXISTS holiday;

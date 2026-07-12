-- ADR 0002: 발송 결과를 라이프사이클 status 로 승격한다.
-- fcm_result(SUCCESS/FAILED/SKIPPED) -> status(SENT/FAILED/DEAD/SKIPPED).
-- 단일 테이블 상태 컬럼(별도 감사/outbox 테이블 없음).
ALTER TABLE notification_log RENAME COLUMN fcm_result TO status;

-- 기존 값 백필: SUCCESS -> SENT. FAILED/SKIPPED 는 그대로, DEAD 는 신규(기존 행 없음).
UPDATE notification_log SET status = 'SENT' WHERE status = 'SUCCESS';

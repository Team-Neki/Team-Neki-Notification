-- 공유 DB 스키마 (테스트 시드용).
-- 출처: 공유 서비스 DDL. #290 미병합분(withdrawn_at)과 MARKETING 약관을 포함한다.
-- WARNING: FK 제약은 의도적으로 생략(테스트 시드 단순화). 실제 공유 DB에는
--   TB_USER_TERM_AGREEMENT.user_id→TB_USERS.id, .term_id→TB_TERM.id FK가 존재한다.
--   공유 서버(Team-Neki-Server) DDL 변경 시 이 파일을 함께 동기화할 것.
--   마지막 동기화 기준: Team-Neki-Server V18 + #290(withdrawn_at, MARKETING 약관).
CREATE TABLE TB_USERS (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255), password VARCHAR(255), oid VARCHAR(255), name VARCHAR(100),
  provider_type VARCHAR(10) NOT NULL,
  profile_image_id VARCHAR(255), role VARCHAR(255) NOT NULL DEFAULT 'ROLE_USER',
  created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL
);
CREATE TABLE TB_TERM (
  id BIGSERIAL PRIMARY KEY,
  term_type VARCHAR(50) NOT NULL, title VARCHAR(100) NOT NULL, url VARCHAR(500) NOT NULL,
  version VARCHAR(20) NOT NULL, is_required BOOLEAN NOT NULL DEFAULT true,
  is_active BOOLEAN NOT NULL DEFAULT true, display_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL
);
CREATE TABLE TB_USER_TERM_AGREEMENT (
  user_id BIGINT NOT NULL, term_id BIGINT NOT NULL, agreed_at TIMESTAMP NOT NULL,
  term_version VARCHAR(20) NOT NULL, withdrawn_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (user_id, term_id)
);
CREATE TABLE TB_PHOTO_IMAGE (
  id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL, media_id BIGINT NOT NULL,
  memo VARCHAR(255), created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
  deleted_at TIMESTAMP NULL, upload_method VARCHAR(15) NULL, captured_at TIMESTAMP NULL
);

-- reader 통합테스트용 테스트 픽스처 — 공유 DB의 *권위 있는 DDL이 아니다*.
-- 권위 스키마는 Team-Neki-Server( :modules:postgres Flyway V1~V18 )가 소유한다.
-- 여기에는 JdbcSendTargetReader가 *읽는* 테이블/컬럼의 최소 부분집합만 둔다(전체 스키마 사본 아님).
-- #290 미병합분(withdrawn_at)·MARKETING 약관 포함. FK는 픽스처 단순화로 생략.
-- 백엔드 스키마 변경 시 이 픽스처를 함께 동기화할 것. 마지막 기준: Team-Neki-Server V18 + #290.
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

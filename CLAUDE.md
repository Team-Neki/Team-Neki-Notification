# CLAUDE.md — 문서 라우터

이 파일은 **라우터**다. 작업 성격에 맞는 문서를 먼저 읽고 시작하라. 각 문서가 해당 영역의 기준(SSOT)이며, 코드와 문서가 어긋나면 **코드가 진실**이고 문서를 갱신한다.

## 코딩 규칙

- 코드 산출물 전반(소스·주석·설정·커밋 메시지)에 **이모지 금지**.
- `domain` 모듈은 프레임워크에 의존하지 않는다(불변식). 도메인 순수성을 깨지 말 것.
- 데이터 접근은 **jOOQ + Flyway**로 통일한다. JPA·JdbcTemplate·QueryDSL 도입 금지([ADR 0001](docs/adr/0001-jooq-over-jpa.md)).
- TDD(테스트 우선). **테스트 종류별 도구 구분**:
  - **영속성/통합 테스트**(infra 어댑터·Job E2E·Flyway/스키마 검증 등 실제 DB 접근) → **Testcontainers(PostgreSQL)**. 이 테스트들 때문에 실행 시 **Docker 필요**. 인메모리 H2 등 대체 DB로 우회하지 않는다. 예: `NotificationLogStoreAdapter`/`NotificationHistStoreAdapter`, `NotificationJobE2ETest`, `SchemaMigrationValidationTest`.
  - **순수 단위 테스트**(DB를 접근하지 않는 도메인 로직·정책·판정, application 서비스의 분기 로직) → **JUnit5 + MockK**로 작성하고 컨테이너를 띄우지 않는다(불필요한 기동 오버헤드·Docker 종속 회피). 예: `NotificationProcessorTest`, `MessageRendererTest`, `NotificationSendServiceTest`.
  - 판단 기준: **테스트가 실제 SQL/스키마를 검증하면 Testcontainers, 순수 로직 분기만 검증하면 MockK.**

## 무엇을 읽을까 (라우팅 표)

| 이럴 때 | 먼저 읽을 문서 |
| --- | --- |
| **정책/스펙** — 무엇을·누구에게·언제 보내는가, 대상/동의/중복 규칙 | [docs/prd/notification-policy.md](docs/prd/notification-policy.md) |
| **문구/톤/변수** 수정 (SSOT) | [docs/prd/notification-copy-spec.md](docs/prd/notification-copy-spec.md) |
| **모듈 구조/의존 규칙/기술 스택** 파악 | [docs/lld/architecture.md](docs/lld/architecture.md) |
| **발송 잡 로직**, 중복 방지, 멱등성, rollout 재전송 | [docs/lld/batch-pipeline.md](docs/lld/batch-pipeline.md) |
| **데이터 접근** — jOOQ/Flyway, 소유/외부 테이블, 렌더 케이스, 스키마 의존성 | [docs/lld/data-access.md](docs/lld/data-access.md) |
| **스케줄러/설정/기능 플래그/프로파일** | [docs/lld/scheduling-and-config.md](docs/lld/scheduling-and-config.md) |
| **공휴일** 시드/발송일 판정 | [docs/lld/holiday-sync.md](docs/lld/holiday-sync.md) |
| **파일별 근거 주석** 인덱스(왜 이렇게 짰나) | [docs/lld/code-notes.md](docs/lld/code-notes.md) |
| **배포/운영** 절차, 필수 주입값 | [docs/runbook/deployment.md](docs/runbook/deployment.md) |
| **기동 실패**(Flyway/공유 DB) 대응 | [docs/runbook/flyway-shared-db-migration-failure.md](docs/runbook/flyway-shared-db-migration-failure.md) |
| **아키텍처 결정** 배경 | [docs/adr/](docs/adr/) |

## 디렉토리 규약

- `docs/prd/` — **정책(무엇/왜)**. 제품·발송·문구·대상 규칙.
- `docs/lld/` — **구현 상세(어떻게)**. 코드 스냅샷 기준 설계.
- `docs/adr/` — **결정 기록**. 되돌리기 어려운 선택과 근거.
- `docs/runbook/` — **운영 절차**. 배포·장애 대응.
- `docs/superpowers/specs/` — **역사적 설계 기록**. 초기 스펙(일부 결정은 ADR로 대체됨 — 예: "쓰기 JPA"는 폐기). 현행 기준은 위 prd/lld.

## 새 문서를 쓸 때

- 구현 방법·코드 구조 → `docs/lld/`. 정책·규칙·발송 대상 → `docs/prd/`. 되돌리기 어려운 결정 → `docs/adr/`. 운영 절차 → `docs/runbook/`.
- 새 문서를 만들면 위 **라우팅 표에 한 줄** 추가한다.
- 코드에서 문서를 참조하는 주석은 파일 경로를 정확히(예: `docs/lld/code-notes.md`).

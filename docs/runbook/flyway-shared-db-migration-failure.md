# 런북 — Flyway 마이그레이션 실패 (공유 DB / 기동 불가)

- 대상: `neki-notification` 배치 앱 (prod)
- 최초 작성: 2026-06-30
- 관련 PR: #23(datasource 설정) · #24(Flyway history 테이블 분리)
- 관련 ADR: [0001 — jOOQ + Flyway 채택](../adr/0001-jooq-over-jpa.md)

이 앱은 `team-neki-server`와 **동일한 PostgreSQL DB를 공유**한다. 공유 DB 환경의 Flyway 설정 실수는 곧 기동 실패(Pod `CrashLoopBackOff`)로 이어진다. 이 문서는 그 증상·진단·복구 절차를 정리한다.

---

## 1. 증상

- ArgoCD에서 `neki-notification` Pod이 `0/1`, `CrashLoopBackOff`
- Pod 로그에 다음 중 하나:
  - `Migration checksum mismatch for migration version 1` (또는 2) — **history 테이블 공유 충돌**
  - `Found non-empty schema(s) ... but no schema history table!` — **baseline 설정 누락**
  - `Failed to configure a DataSource: 'url' attribute is not specified` — **datasource 미설정**(별건, 5절 참조)

## 2. 빠른 진단

```bash
# Pod 상태
kubectl get pods -n prod | grep neki-notification

# 실패 로그 (마이그레이션 에러 라인 확인)
kubectl logs -n prod <pod> --previous | grep -iE "flyway|migration|schema|datasource"

# 현재 배포 이미지
kubectl get deploy -n prod neki-notification \
  -o jsonpath='{.spec.template.spec.containers[0].image}'
```

판별:
- `checksum mismatch` / `Found non-empty schema` → **본 문서의 3절**(공유 history 충돌)
- `Failed to configure a DataSource` → **5절**(datasource)

## 3. 근본 원인 — `flyway_schema_history` 테이블 공유

`neki-notification`과 `team-neki-server`가 같은 DB를 쓰는데, **둘 다 기본 `flyway_schema_history` 테이블을 사용**하면 서로의 마이그레이션이 같은 version 번호로 충돌한다.

| version | server 가 적용 (기본 history) | notification 이 적용하려는 것 |
|---|---|---|
| V1 | `V1__create_users_table.sql` | `V1__notification_schema.sql` (notification_log, holiday) |
| V2 | `V2__create_folder_and_photo_image_table.sql` | `V2__spring_batch_schema.sql` (Spring Batch 메타) |

같은 version·다른 스크립트 → checksum 불일치 → notification 기동 실패.

## 4. 해결 — 앱 전용 history 테이블로 분리

`apps/batch/src/main/resources/application.yml`:

```yaml
spring:
  flyway:
    table: flyway_schema_history_notification  # server 와 독립된 전용 history
    baseline-on-migrate: true                  # 공유 스키마가 non-empty 이므로 채택 필요
    baseline-version: "0"                       # V1 부터 적용 (기본 1 이면 V1 이 skip 됨)
```

분리 후 같은 DB 안에 history 테이블이 **앱별로 2개** 공존한다.

| history 테이블 | 소유 앱 |
|---|---|
| `flyway_schema_history` | team-neki-server |
| `flyway_schema_history_notification` | neki-notification |

### ⚠️ `baseline-version: "0"`이 반드시 필요한 이유

공유 `public` 스키마에는 server 테이블이 가득(non-empty)하다.
- `baseline-on-migrate: true` + 기본 `baseline-version: 1` → 새 history 테이블을 **버전 1로 baseline 처리**하여 **V1이 skip** → `notification_log`/`holiday`가 생성되지 않아 런타임에 또 실패.
- `baseline-version: "0"` → 버전 0으로 baseline → V1·V2 **모두 적용**.

## 5. (별건) datasource 미설정

`Failed to determine a suitable driver class`는 위 history 충돌과 무관한 별도 원인이다. prod 프로파일에 datasource가 없으면 발생한다. `application-prod.yml`에 server와 동일한 공유 DB 접속 정보(ENC) + `jasypt.encryptor.password=${JASYPT_PASSWORD}`가 있어야 한다. (PR #23 참조)

## 6. 하지 말아야 할 우회책

| 시도 | 왜 안 되는가 |
|---|---|
| `flyway repair` / `repair-on-migrate: true` | 공유 history의 checksum을 notification 기준으로 덮어써 **이번엔 server가 깨짐**. 게다가 V1/V2가 "적용됨"으로 남아 notification 소유 테이블은 **생성 안 됨**. |
| `flyway.enabled: false` | `notification_log`/`holiday`/Spring Batch 메타가 아예 안 생겨 Batch 초기화 실패. |
| `validate-on-migrate: false` | checksum 에러만 사라질 뿐, 공유 history의 server V1/V2 때문에 notification V1/V2가 **skip** → 동일하게 테이블 미생성. |

→ 근본 원인이 "history 테이블 공유"이므로 **테이블 분리(4절)만이 정답**이다.

## 7. 복구 — DB 수동 조치 필요한가?

**대부분 불필요하다.** notification은 마이그레이션 *검증 단계*에서 실패하면 history에 **아무 row도 쓰지 않는다**. 즉 공유 `flyway_schema_history`는 server 것만 있고 오염되지 않았다. 전용 테이블로 전환(4절) 후 재배포하면 새 `flyway_schema_history_notification`에 V1·V2가 처음부터 깨끗하게 적용된다.

> 단, 과거에 notification이 **한 번이라도 성공 마이그레이션**해 소유 테이블(`notification_log` 등)을 만든 적이 있다면, V1의 `CREATE TABLE`이 충돌할 수 있다. 그 경우에만 해당 테이블 존재 여부를 확인한다.

## 8. 검증

- 자동: `apps/batch/.../FlywayHistoryIsolationTest` — prod 조건(충돌 history + non-empty 스키마)을 재현해 (1) 전용 테이블+baseline 0으로 V1부터 정상 적용, (2) `baseline-on-migrate: false`는 non-empty 스키마에서 실패함을 검증.
  ```bash
  ./gradlew :apps:batch:test --tests "*FlywayHistoryIsolationTest"
  ```
- 배포 후:
  ```bash
  kubectl get pods -n prod | grep neki-notification        # 1/1 Running
  kubectl logs -n prod <pod> | grep -iE "Started Notification|Tomcat started"
  ```

## 9. 배포 절차

prod 배포는 **수동(`workflow_dispatch`)** 전용이다.

```bash
gh workflow run deploy-prod.yml --ref main
```

→ 이미지 빌드/푸시 → GitOps 이미지 태그 갱신 → ArgoCD 자동 sync.

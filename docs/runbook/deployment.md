# 런북 — 배포 / 운영

- 대상: `neki-notification` 배치 앱 (prod, K3s + ArgoCD GitOps)
- 관련: `.github/workflows/deploy-prod.yml`, `Dockerfile`, `docs/lld/scheduling-and-config.md`

## 1. 배포 절차 (수동 전용)

prod 배포는 **수동(`workflow_dispatch`)** 전용이다. main 머지로 자동 배포되지 않는다.

```bash
gh workflow run deploy-prod.yml --ref main
```

파이프라인(`deploy-prod.yml`):
1. `./gradlew :apps:batch:bootJar -x test` — 배포 산출물은 `apps:batch` bootJar 하나뿐.
2. Docker 이미지 빌드/푸시 — 태그 `{version}-{short_sha}` + `latest`.
3. GitOps 레포(`Team-Neki/Team-Neki-GitOps`)의 `overlays/prod/notification-deployment.yaml` image 태그 갱신 → 커밋/푸시.
4. ArgoCD가 변경 감지 → 자동 sync.

> 사전 준비: GitOps 레포에 해당 deployment YAML이 존재하고 `image:` 라인 형식이 일치해야 한다. Secrets: `DOCKER_USERNAME/PASSWORD`, `GITOPS_PAT`.

## 2. 런타임 형상 (Dockerfile)

- 베이스: `eclipse-temurin:21-jre-alpine`, non-root(`spring`), Spring Boot Layered JAR.
- ENV: `TZ=Asia/Seoul`, `SPRING_PROFILES_ACTIVE=prod`.
- 엔트리포인트: `JarLauncher`. 앱 내부 `@Scheduled` cron으로 잡 구동, actuator 헬스(8080) 노출.

## 3. 운영 필수 주입값

| 항목 | 주입 | 없으면 |
| --- | --- | --- |
| `JASYPT_PASSWORD` (env, `neki-secrets`) | `application-prod.yml`의 ENC datasource 복호화 키 | 기동 실패 |
| Firebase 서비스계정 JSON | `/etc/firebase/firebase-service-account.json` Secret 볼륨 마운트 | `fcm.enabled=true`라 빈 생성 실패 → CrashLoop |
| DB `CREATE TABLE` 권한 | 공유 DB 연결 계정 | Flyway 소유 테이블 생성 실패 |

prod 프로파일이 켜는 플래그: `fcm.enabled`, `batch.scheduling-enabled`, `batch.holiday-sync-enabled` = `true`. 상세: `docs/lld/scheduling-and-config.md §4·§5`.

## 4. 기능 플래그는 런타임 토글 불가

`@ConditionalOnProperty`는 **기동 시 1회만 평가**된다. 스케줄러/FCM/공휴일시드 on-off 변경은 **pod 재시작**으로만 반영된다.

## 5. 배포 검증

```bash
kubectl get pods -n prod | grep neki-notification            # 1/1 Running
kubectl logs -n prod <pod> | grep -iE "Started Notification|Tomcat started"
# 실발송 모드 확인(FcmPushSender init 로그) / 무발송이면 LoggingPushSender WARN
kubectl logs -n prod <pod> | grep -iE "FcmPushSender|LoggingPushSender"
```

수동 스모크(운영 주의 — 실발송):
```bash
# 단건 푸시 (notification_log 미기록)
curl -X POST "http://<host>/test/notifications/push?token=<FCM_TOKEN>"
# 잡 수동 기동
curl -X POST "http://<host>/test/notifications/jobs/weekendExploreJob"
```

## 6. 흔한 기동 실패

| 증상 | 원인 | 문서 |
| --- | --- | --- |
| `CrashLoopBackOff` + `checksum mismatch` / `Found non-empty schema` | Flyway history 공유 충돌 | [flyway-shared-db-migration-failure.md](flyway-shared-db-migration-failure.md) |
| `Failed to configure a DataSource` / `driver class` | datasource 미설정 / `JASYPT_PASSWORD` 누락 | 위 문서 §5 |
| 기동은 되는데 cron 첫 실행에서 SQL 에러 | 외부 스키마 불일치(리더 jOOQ DSL이나 외부 테이블은 부팅 시 미검증) | `docs/lld/data-access.md §6` |

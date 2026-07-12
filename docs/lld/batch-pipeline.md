# LLD — 배치 파이프라인 / 멱등성

발송 잡 3종의 공통 Reader→Processor→Writer 구조와 중복 발송 방지 메커니즘.
정책적 전달 보장(at-least-once)은 `docs/prd/notification-policy.md §7` 참조.

## 1. 공통 Step 파이프라인

세 잡(WEEKLY/WEEKEND/HOLIDAY)은 동일 골격을 공유하고 **Reader 쿼리와 `NotificationType`만** 다르다. 공통 조립은 `NotificationStepFactory`가 담당한다(OCP: 타입 추가 = `*Job` 클래스 추가).

```
Reader (jOOQ plain SQL)  → 발송 대상 + 변수 원천값 + FCM 토큰 (keyset 페이징, user_id 오름차순)
Processor                → alreadySent 조회 → NotificationProcessor.decide → Send(렌더된 문구) | Skip(null)
Writer                   → FCM 발송(send) → notification_log 적재(save)
```

- **Reader**: `PagingSendTargetItemReader`(keyset 페이징). `user_id > cursor`로 페이지를 당겨 1건씩 흘리고, 빈 페이지를 만나면 소진으로 종료. 단일 인스턴스·단일 스레드 전제.
- **Processor**: `NotificationItemProcessor`. `logStore.alreadySent(userId, type, businessDate)` → `NotificationProcessor.decide()`. Skip이면 `null` 반환해 청크에서 필터링. (동의 재확인은 하지 않음 — 동의는 Reader 쿼리 `push_agreed=true`가 단일 출처.)
- **Writer**: `NotificationItemWriter`. 각 건을 `pushSender.send()` 후 결과(SUCCESS/FAILED/SKIPPED)로 `NotificationLog`를 적재.

## 2. 조립 상수 (`NotificationStepFactory`)

| 상수 | 값 | 의미 |
| --- | --- | --- |
| `CHUNK_SIZE` | **1** | 커밋 단위. 건별 트랜잭션. |
| `PAGE_SIZE` | 100 | DB 조회 단위(≠ 커밋 단위). |

## 3. 중복 방지 (dedup)

- 키: `(user_id, notification_type, business_date)`.
- 1차: Processor의 `alreadySent` 조회(커밋된 이력만 관찰).
- 최종: `notification_log`의 `UNIQUE(user_id, notification_type, business_date)` 제약.

## 4. `CHUNK_SIZE = 1`인 이유 (핵심)

Writer는 **send-then-save** dual-write다: FCM 발송은 트랜잭션 밖 외부 부수효과, 이력 적재는 DB 트랜잭션.

- 청크 > 1이면: 한 건의 `save()` 실패가 **같은 청크에서 이미 FCM 발송된 다른 건들의 적재까지 롤백**시킨다. 재실행 시 `alreadySent`는 커밋된 이력만 보므로, 롤백된 건들이 다시 발송된다(중복).
- 청크 = 1이면: 블라스트 반경이 "실패한 그 1건"으로 한정된다.

## 5. 잔여 중복 창 (at-least-once)

CHUNK_SIZE=1이어도 완전한 exactly-once는 아니다.

- **위험 시나리오**: 한 건에 대해 `send()`는 성공했는데 `save()` 직전/도중 프로세스가 죽으면(예: rollout으로 pod kill), 그 유저는 푸시를 받았지만 이력이 없다 → 같은 `businessDate`로 재실행 시 `alreadySent=false`로 판정되어 **1회 중복 발송**.
- **완화 요인**: `FcmPushSender`는 토큰 단위 전송 실패를 `FcmResult.FAILED`로 흡수해 청크를 중단시키지 않으므로, 청크 중단은 사실상 `save()`(DB) 실패에 한정 → 드묾. 단일 인스턴스 전제라 동시 실행 경합도 없음.
- **완전 at-most-once가 필요해지면**(예: 다중 인스턴스화): 발송 전에 별도 트랜잭션으로 unique 제약을 선점하는 멱등 클레임 패턴으로 강화.

## 6. rollout(재배포) 시 재전송 여부

1. **cron 기반 스케줄** — 재기동만으로 잡이 즉시 뜨지 않는다(`ApplicationRunner` 아님). 다음 cron 시각까지 재실행 없음. → 발송 시간대 밖 배포는 재실행 자체가 없다.
2. **`alreadySent` 멱등성** — 같은 날 재실행되어도 이미 발송/적재된 유저는 스킵.
3. **중복 기동 방어** — `NotificationJobLauncher.launch()`가 `JobExplorer.findRunningJobExecutions`로 실행 중 동일 잡이 있으면 건너뜀.

→ 실질 재전송 위험은 §5의 "발송 도중 kill + 당일 재실행"이 겹치는 최대 1건.

## 7. 중복/동시 기동 방어 상세

- Launcher는 매 기동에 `launchedAt=clock.millis()`를 JobParameter로 붙여 **JobInstance를 유일화**한다. 따라서 Spring Batch의 "동일 파라미터 완료 인스턴스 재실행 금지"에 기댈 수 없고, 중복 방지는 전적으로 `alreadySent` + UNIQUE 제약이 담당한다(의도된 설계).
- `findRunningJobExecutions` 가드는 **단일 JVM 내에서만** 유효. 다중 인스턴스는 분산 락 필요.

## 8. Job별 차이

| Job | Reader | businessDate 사용 | 변수 |
| --- | --- | --- | --- |
| `WeeklyReminderJob` | `WeeklyReminderTargetReader` | `businessDate - 7일` 업로드 조회 | `[최근 업로드 요일]` |
| `WeekendExploreJob` | `WeekendExploreTargetReader` | 미사용(전원) | 없음 |
| `HolidayExploreJob` | `HolidayExploreTargetReader` (발송일 아니면 빈 Reader) | 공휴일 발송일 판정 | `[공휴일명]`(실행 단위 값) |

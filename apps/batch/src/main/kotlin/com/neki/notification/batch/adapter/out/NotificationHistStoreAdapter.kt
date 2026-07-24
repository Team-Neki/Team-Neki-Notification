package com.neki.notification.batch.adapter.out

import com.neki.notification.application.port.out.NotificationHistStore
import com.neki.notification.domain.model.NotificationType
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * [NotificationHistStore]의 jOOQ 구현. `tb_notification_hist`는 백엔드(Team-Neki-Server V22) 소유
 * 외부 테이블이라 codegen 대상이 아니라 이름 기반 참조(`DSL.table`/`DSL.field`)로 INSERT 한다
 * (`tb_notification` 리더와 동일 방식). `id`·`created_at`·`updated_at`은 DB DEFAULT에 위임한다.
 *
 * best-effort 격리(H-3): 적재는 [Propagation.REQUIRES_NEW]로 청크 트랜잭션과 **별도 커넥션**에서
 * 수행한다. PostgreSQL은 트랜잭션 내 한 statement라도 실패하면 트랜잭션 전체를 abort하므로, 같은
 * 청크 트랜잭션에서 INSERT가 실패하면 이미 적재한 `notification_log`까지 커밋 시 롤백된다. 별도
 * 트랜잭션이라야 hist 실패가 커넥션을 오염시키지 않아 호출자([NotificationSendService])가 예외를
 * 삼켜 발송/`notification_log`를 지킬 수 있다.
 */
@Component
class NotificationHistStoreAdapter(
    private val dsl: DSLContext,
) : NotificationHistStore {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun save(userId: Long, type: NotificationType, title: String, body: String, link: String?) {
        dsl.insertInto(HIST)
            .set(USER_ID, userId)
            .set(TYPE, type.name)
            .set(TITLE, title)
            .set(BODY, body)
            .set(LINK, link)
            .execute()
    }

    private companion object {
        val HIST = DSL.table(DSL.name("tb_notification_hist"))
        val USER_ID: Field<Long> = DSL.field(DSL.name("user_id"), Long::class.java)
        val TYPE: Field<String> = DSL.field(DSL.name("type"), String::class.java)
        val TITLE: Field<String> = DSL.field(DSL.name("title"), String::class.java)
        val BODY: Field<String> = DSL.field(DSL.name("body"), String::class.java)
        val LINK: Field<String?> = DSL.field(DSL.name("link"), String::class.java)
    }
}

package com.neki.notification.batch.adapter.out.read

import com.neki.notification.domain.model.MessageVariable
import com.neki.notification.domain.model.SendTarget
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.Result
import org.jooq.impl.DSL

/**
 * 세 TargetReader의 공통 jOOQ 조회 골격. [query]가 동의·keyset·정렬·LIMIT를 강제해 리더가 동의 필터를
 * 빠뜨리지 못하게 한다(동의 규칙 단일 출처).
 */
internal object TargetReaderSupport {

    private val NOTIFICATION = DSL.table(DSL.name("tb_notification")).`as`("n")
    val USER_ID: Field<Long> = DSL.field(DSL.name("n", "user_id"), Long::class.java)
    val DEVICE_TOKEN: Field<String> = DSL.field(DSL.name("n", "device_token"), String::class.java)
    private val PUSH_AGREED: Field<Boolean> = DSL.field(DSL.name("n", "push_agreed"), Boolean::class.java)

    fun query(
        dsl: DSLContext,
        afterUserId: Long,
        pageSize: Int,
        extraColumns: List<Field<*>> = emptyList(),
        extraCondition: Condition = DSL.noCondition(),
    ): Result<Record> {
        val columns: List<Field<*>> = listOf(USER_ID, DEVICE_TOKEN) + extraColumns
        return dsl.select(columns)
            .from(NOTIFICATION)
            .where(PUSH_AGREED.isTrue.and(USER_ID.gt(afterUserId)).and(extraCondition))
            .orderBy(USER_ID.asc())
            .limit(pageSize)
            .fetch()
    }

    fun sendTarget(
        record: Record,
        variables: Map<MessageVariable, String?> = emptyMap(),
    ): SendTarget =
        SendTarget(
            userId = record.get(USER_ID),
            fcmToken = record.get(DEVICE_TOKEN),
            variables = variables,
        )
}

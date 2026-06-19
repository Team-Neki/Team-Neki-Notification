package com.neki.notification.batch.adapter.out.read

import com.neki.notification.domain.model.MessageVariable
import com.neki.notification.domain.model.SendTarget
import org.jooq.Record

internal object TargetReaderSupport {

    const val PAGING_PREDICATE = "n.push_agreed = true AND n.user_id > ?"

    const val PAGING_TAIL = "ORDER BY n.user_id LIMIT ?"

    fun sendTarget(
        record: Record,
        variables: Map<MessageVariable, String?> = emptyMap(),
    ): SendTarget =
        SendTarget(
            userId = record.get("user_id", Long::class.java),
            fcmToken = record.get("device_token", String::class.java),
            pushConsent = true,
            variables = variables,
        )
}

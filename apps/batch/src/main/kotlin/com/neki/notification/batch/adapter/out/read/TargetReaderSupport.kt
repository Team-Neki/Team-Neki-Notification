package com.neki.notification.batch.adapter.out.read

import com.neki.notification.domain.model.MessageVariable
import com.neki.notification.domain.model.SendTarget
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import java.sql.ResultSet

internal object TargetReaderSupport {

    const val PAGING_PREDICATE = "n.push_agreed = true AND n.user_id > :after"

    const val PAGING_TAIL = "ORDER BY n.user_id LIMIT :limit"

    fun pagingParams(afterUserId: Long, pageSize: Int): MapSqlParameterSource =
        MapSqlParameterSource()
            .addValue("after", afterUserId)
            .addValue("limit", pageSize)

    fun sendTarget(
        rs: ResultSet,
        variables: Map<MessageVariable, String?> = emptyMap(),
    ): SendTarget =
        SendTarget(
            userId = rs.getLong("user_id"),
            fcmToken = rs.getString("device_token"),
            pushConsent = true,
            variables = variables,
        )
}

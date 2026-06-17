package com.neki.notification.infra.persistence

import com.neki.notification.domain.model.NotificationType
import com.neki.notification.domain.model.SendTarget
import com.neki.notification.domain.port.SendTargetReader
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.LocalDate

/**
 * 공유 DB(JDBC) 기반 [SendTargetReader] 구현.
 *
 * 설계 메모(P3):
 * - 동의 투영은 필터가 아니다: MARKETING 약관 동의/철회/미동의/비-MARKETING 동의를 모두
 *   `pushConsent` 값으로 투영하되 결과에서 제외하지 않는다(동의 최종 재확인은 Processor 책임).
 * - keyset 페이징은 `user_id` 오름차순 + `afterUserId` 커서로 수행한다.
 * - fcmToken은 #291(토큰 저장소) 미존재로 빈 문자열("")을 투영한다.
 * - variables는 빈 맵으로 둔다(문구 변수 조달은 후속).
 * - 현재 RED 단계에서는 WEEKEND_EXPLORE 경로만 다룬다(WEEKLY/HOLIDAY는 P3b/P3c).
 *
 * Spring 어노테이션을 두지 않은 순수 클래스다(빈 등록은 후속 config).
 */
class JdbcSendTargetReader(
    private val jdbc: NamedParameterJdbcTemplate,
) : SendTargetReader {
    override fun read(
        type: NotificationType,
        businessDate: LocalDate,
        afterUserId: Long?,
        limit: Int,
    ): List<SendTarget> = when (type) {
        NotificationType.WEEKEND_EXPLORE -> readWeekendExplore(afterUserId, limit)
        NotificationType.WEEKLY_REMINDER -> TODO("P3b")
        NotificationType.HOLIDAY_EXPLORE -> TODO("P3c")
    }

    /**
     * WEEKEND_EXPLORE 발송 대상 조회.
     *
     * 전체 유저를 keyset(`user_id` 오름차순 + `afterUserId` 커서)으로 조회하며,
     * MARKETING 약관 동의 여부를 EXISTS 서브쿼리로 `pushConsent`에 투영한다
     * (행 증식 방지). 동의는 필터가 아니라 투영이므로 미동의 유저도 결과에 포함된다.
     */
    private fun readWeekendExplore(afterUserId: Long?, limit: Int): List<SendTarget> {
        val sql =
            """
            SELECT u.id AS user_id,
                   EXISTS (
                     SELECT 1 FROM TB_USER_TERM_AGREEMENT uta
                     JOIN TB_TERM t ON t.id = uta.term_id
                     WHERE uta.user_id = u.id
                       AND t.term_type = 'MARKETING' AND t.is_active = true
                       AND uta.withdrawn_at IS NULL
                   ) AS push_consent
            FROM TB_USERS u
            WHERE u.id > :afterUserId
            ORDER BY u.id ASC
            LIMIT :limit
            """.trimIndent()

        val params = MapSqlParameterSource()
            .addValue("afterUserId", afterUserId ?: 0L)
            .addValue("limit", limit)

        return jdbc.query(sql, params) { rs, _ ->
            SendTarget(
                userId = rs.getLong("user_id"),
                fcmToken = "",
                pushConsent = rs.getBoolean("push_consent"),
                variables = emptyMap(),
            )
        }
    }
}

package com.neki.notification.domain.policy

import com.neki.notification.domain.model.MessageTone
import com.neki.notification.domain.model.MessageVariable
import com.neki.notification.domain.model.NotificationType
import com.neki.notification.domain.model.RenderedMessage
import com.neki.notification.domain.model.fallbackTone

object MessageRenderer {

    private data class Template(
        val title: String,
        val body: String,
        val requiredVariable: MessageVariable? = null,
    )

    private fun templateFor(type: NotificationType, tone: MessageTone): Template = when (type) {
        NotificationType.WEEKLY_REMINDER -> when (tone) {
            MessageTone.INFORMATIVE -> Template(
                title = "일주일 전 사진이 있어요",
                body = "네키에 저장한 네컷을 다시 확인해보세요.",
            )
            MessageTone.FRIENDLY -> Template(
                title = "벌써 일주일 전 네컷이에요",
                body = "지난 사진을 네키에서 다시 꺼내보세요.",
            )
            MessageTone.SUGGESTIVE -> Template(
                title = "{최근 업로드 요일}처럼 오늘도 남겨볼까요?",
                body = "오늘 찍은 사진도 네키에 정리해보세요.",
                requiredVariable = MessageVariable.RECENT_UPLOAD_DAY,
            )
        }

        NotificationType.WEEKEND_EXPLORE -> when (tone) {
            MessageTone.INFORMATIVE -> Template(
                title = "주말 전 포토부스 확인하기",
                body = "가까운 포토부스를 네키 지도에서 확인해보세요.",
            )
            MessageTone.FRIENDLY -> Template(
                title = "이번 주말엔 어디서 찍을까요?",
                body = "약속 전에 근처 포토부스를 미리 찾아보세요.",
            )
            MessageTone.SUGGESTIVE -> Template(
                title = "약속 전에 미리 찾아보세요",
                body = "가까운 포토부스를 네키 지도에서 확인해보세요.",
            )
        }

        NotificationType.HOLIDAY_EXPLORE -> when (tone) {
            MessageTone.INFORMATIVE -> Template(
                title = "{공휴일명} 포토부스 확인하기",
                body = "쉬는 날 방문할 포토부스를 네키 지도에서 확인해보세요.",
                requiredVariable = MessageVariable.HOLIDAY_NAME,
            )
            MessageTone.FRIENDLY -> Template(
                title = "{공휴일명}에 약속 있으신가요?",
                body = "약속 전에 근처 포토부스를 미리 확인해보세요!",
                requiredVariable = MessageVariable.HOLIDAY_NAME,
            )
            MessageTone.SUGGESTIVE -> Template(
                title = "쉬는 날 가기 좋은 포토부스",
                body = "네키 지도에서 가까운 포토부스를 확인해보세요.",
            )
        }
    }

    fun render(
        type: NotificationType,
        assignedTone: MessageTone,
        variables: Map<MessageVariable, String?>,
    ): RenderedMessage {
        val template = templateFor(type, assignedTone)
        val required = template.requiredVariable

        if (required == null) {
            return RenderedMessage(
                title = template.title,
                body = template.body,
                actualTone = assignedTone,
                variableApplied = false,
            )
        }

        val value = variables[required]

        if (!value.isNullOrBlank()) {
            return RenderedMessage(
                title = template.title.replace("{${required.token}}", value),
                body = template.body.replace("{${required.token}}", value),
                actualTone = assignedTone,
                variableApplied = true,
            )
        }

        val fallback = type.fallbackTone
        val fallbackTemplate = templateFor(type, fallback)
        return RenderedMessage(
            title = fallbackTemplate.title,
            body = fallbackTemplate.body,
            actualTone = fallback,
            variableApplied = false,
        )
    }
}

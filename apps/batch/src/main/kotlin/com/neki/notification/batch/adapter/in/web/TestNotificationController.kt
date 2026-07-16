package com.neki.notification.batch.adapter.`in`.web

import com.neki.notification.application.port.out.PushSender
import com.neki.notification.batch.adapter.`in`.NotificationJobLauncher
import com.neki.notification.domain.model.FcmResult
import com.neki.notification.domain.model.MessageTone
import com.neki.notification.domain.model.RenderedMessage
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 테스트용 수동 트리거 API(pod 내부 전용). 스케줄(cron)을 기다리지 않고 잡 실행/푸시 발송을 검증한다.
 *
 * 주의: `neki.test-api.enabled=true`일 때만 빈이 등록된다(기본 비활성). 인증이 없으므로 k8s Service/
 * Ingress로 외부 노출 금지 — pod 내부 호출 전용. 호출 시 실제 배치 실행·FCM 발송이 일어난다
 * (neki.fcm.enabled=true 인 prod 는 실발송, 비활성 환경은 LoggingPushSender 로 SKIPPED).
 */
@RestController
@RequestMapping("/test/notifications")
@ConditionalOnProperty(prefix = "neki.test-api", name = ["enabled"], havingValue = "true")
class TestNotificationController(
    private val launcher: NotificationJobLauncher,
    private val pushSender: PushSender,
) {
    /**
     * 알림 배치 잡을 이름으로 수동 기동한다. (weeklyReminderJob / weekendExploreJob / holidayExploreJob)
     * jobLauncher 가 동기 실행이므로 잡 종료까지 응답이 블로킹된다.
     */
    @PostMapping("/jobs/{jobName}")
    fun triggerJob(
        @PathVariable jobName: String,
        @RequestParam(required = false) businessDate: String?,
    ): ResponseEntity<TriggerJobResponse> {
        val date = businessDate?.let { LocalDate.parse(it) }

        val execution = try {
            launcher.launchByName(jobName, date)
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest()
                .body(TriggerJobResponse(jobName, null, "UNKNOWN_JOB", e.message))
        }

        if (execution == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(TriggerJobResponse(jobName, null, "ALREADY_RUNNING", "이미 실행 중인 잡입니다."))
        }

        return ResponseEntity.ok(
            TriggerJobResponse(
                jobName = jobName,
                executionId = execution.id,
                status = execution.status.name,
                message = execution.exitStatus.exitCode,
            ),
        )
    }

    /** 단건 FCM 푸시 발송 스모크 테스트. notification_log 에는 기록하지 않는다. */
    @PostMapping("/push")
    fun sendPush(
        @RequestParam token: String,
        @RequestParam(defaultValue = "알림") title: String,
        @RequestParam(defaultValue = "테스트 푸시입니다.") body: String,
        @RequestParam(required = false) tone: MessageTone?,
    ): PushTestResponse {
        val message = RenderedMessage(
            title = title,
            body = body,
            actualTone = tone ?: MessageTone.INFORMATIVE,
            variableApplied = false,
        )
        val result: FcmResult = pushSender.send(token, message)
        return PushTestResponse(token, result)
    }
}

data class TriggerJobResponse(
    val jobName: String,
    val executionId: Long?,
    val status: String,
    val message: String?,
)

data class PushTestResponse(
    val token: String,
    val result: FcmResult,
)

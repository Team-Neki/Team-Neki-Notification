package com.neki.notification.application.port.out

import com.neki.notification.domain.model.FcmResult
import com.neki.notification.domain.model.RenderedMessage

/**
 * 푸시 발송 포트 (batch-design §4). infra가 Firebase Admin SDK 어댑터로 구현한다.
 */
fun interface PushSender {
    /**
     * 단일 디바이스 토큰으로 메시지를 발송한다.
     * @return 발송 성공/실패 (무효 토큰·전송 실패는 [FcmResult.FAILED]).
     */
    fun send(token: String, message: RenderedMessage): FcmResult
}

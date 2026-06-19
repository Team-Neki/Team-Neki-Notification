package com.neki.notification.application.port.out

import com.neki.notification.domain.model.FcmResult
import com.neki.notification.domain.model.RenderedMessage

fun interface PushSender {
    fun send(token: String, message: RenderedMessage): FcmResult
}

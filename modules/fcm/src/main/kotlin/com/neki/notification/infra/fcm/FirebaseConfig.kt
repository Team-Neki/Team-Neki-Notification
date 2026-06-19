package com.neki.notification.infra.fcm

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource

/**
 * Firebase Admin SDK 초기화 (batch-design §4, 기술 인프라 모듈).
 *
 * `neki.fcm.enabled=true` 일 때만 자격증명(`neki.fcm.credentials-location`)을 읽어 [FirebaseApp] 과
 * [FirebaseMessaging] 빈을 등록한다. "어떤 포트를 구현할지"는 앱(adapter/out)의 책임이고,
 * 이 모듈은 FCM 클라이언트 구성만 담당한다.
 */
@Configuration
@ConditionalOnProperty(prefix = "neki.fcm", name = ["enabled"], havingValue = "true")
class FirebaseConfig {

    @Bean
    fun firebaseApp(
        @Value("\${neki.fcm.credentials-location}") credentials: Resource,
    ): FirebaseApp {
        val options = FirebaseOptions.builder()
            .setCredentials(credentials.inputStream.use { GoogleCredentials.fromStream(it) })
            .build()
        return FirebaseApp.getApps().firstOrNull() ?: FirebaseApp.initializeApp(options)
    }

    @Bean
    fun firebaseMessaging(firebaseApp: FirebaseApp): FirebaseMessaging =
        FirebaseMessaging.getInstance(firebaseApp)
}

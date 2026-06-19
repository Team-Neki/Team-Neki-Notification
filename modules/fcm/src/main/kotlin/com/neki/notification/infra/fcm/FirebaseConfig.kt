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

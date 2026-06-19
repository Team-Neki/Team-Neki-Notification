package com.neki.notification.batch.application.job

import com.neki.notification.batch.adapter.out.read.WeekendExploreTargetReader
import com.neki.notification.domain.model.NotificationType
import com.neki.notification.domain.model.PreparedNotification
import com.neki.notification.domain.model.SendTarget
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemReader
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.LocalDate

/**
 * WEEKEND_EXPLORE Job (batch-design §5): 동의자 전원에게 주말 탐방 알림.
 *
 * 대상 = `push_agreed = true` 전원, 변수 없음. 골격 조립은 [NotificationStepFactory]에 위임한다.
 */
@Configuration("weekendExploreJobConfig")
class WeekendExploreJob(
    private val steps: NotificationStepFactory,
    private val reader: WeekendExploreTargetReader,
) {
    @Bean
    @StepScope
    fun weekendItemReader(): ItemReader<SendTarget> =
        steps.pagingReader { after, size -> reader.readPage(after, size) }

    @Bean
    @StepScope
    fun weekendItemProcessor(
        @Value("#{jobParameters['businessDate']}") businessDate: String,
    ): ItemProcessor<SendTarget, PreparedNotification> =
        steps.processor(NotificationType.WEEKEND_EXPLORE, LocalDate.parse(businessDate))

    @Bean
    fun weekendStep(
        weekendItemReader: ItemReader<SendTarget>,
        weekendItemProcessor: ItemProcessor<SendTarget, PreparedNotification>,
    ): Step = steps.chunkStep(STEP_NAME, weekendItemReader, weekendItemProcessor)

    @Bean(name = [JOB_NAME])
    fun weekendExploreJob(weekendStep: Step): Job = steps.singleStepJob(JOB_NAME, weekendStep)

    companion object {
        const val JOB_NAME = "weekendExploreJob"
        const val STEP_NAME = "weekendStep"
    }
}

package com.neki.notification.batch.application.job

import com.neki.notification.batch.adapter.out.read.WeeklyReminderTargetReader
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
 * WEEKLY_REMINDER Job (batch-design §5): 7일 전 업로드 동의자에게 주간 리마인드.
 *
 * Reader가 `businessDate`(JobParameter)를 받아 7일 전 업로드 이력을 조회하고,
 * 변수 `[최근 업로드 요일]`을 채운다. 골격 조립은 [NotificationStepFactory]에 위임한다.
 */
@Configuration("weeklyReminderJobConfig")
class WeeklyReminderJob(
    private val steps: NotificationStepFactory,
    private val reader: WeeklyReminderTargetReader,
) {
    @Bean
    @StepScope
    fun weeklyItemReader(
        @Value("#{jobParameters['businessDate']}") businessDate: String,
    ): ItemReader<SendTarget> {
        val date = LocalDate.parse(businessDate)
        return steps.pagingReader { after, size -> reader.readPage(date, after, size) }
    }

    @Bean
    @StepScope
    fun weeklyItemProcessor(
        @Value("#{jobParameters['businessDate']}") businessDate: String,
    ): ItemProcessor<SendTarget, PreparedNotification> =
        steps.processor(NotificationType.WEEKLY_REMINDER, LocalDate.parse(businessDate))

    @Bean
    fun weeklyStep(
        weeklyItemReader: ItemReader<SendTarget>,
        weeklyItemProcessor: ItemProcessor<SendTarget, PreparedNotification>,
    ): Step = steps.chunkStep(STEP_NAME, weeklyItemReader, weeklyItemProcessor)

    @Bean(name = [JOB_NAME])
    fun weeklyReminderJob(weeklyStep: Step): Job = steps.singleStepJob(JOB_NAME, weeklyStep)

    companion object {
        const val JOB_NAME = "weeklyReminderJob"
        const val STEP_NAME = "weeklyStep"
    }
}

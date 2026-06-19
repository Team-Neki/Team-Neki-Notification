package com.neki.notification.batch.application.job

import com.neki.notification.application.port.out.HolidayCalendar
import com.neki.notification.batch.adapter.out.read.HolidayExploreTargetReader
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
 * HOLIDAY_EXPLORE Job (batch-design §P5): 공휴일 발송일에 최근 1달 업로드 동의자에게 알림.
 *
 * 매일 깨어나 [HolidayCalendar]로 발송일 여부를 판정한다. 발송일이 아니면 빈 Reader로 0건 처리하고,
 * 발송일이면 변수 `[공휴일명]`을 채워 발송한다. 골격 조립은 [NotificationStepFactory]에 위임한다.
 */
@Configuration("holidayExploreJobConfig")
class HolidayExploreJob(
    private val steps: NotificationStepFactory,
    private val reader: HolidayExploreTargetReader,
    private val holidayCalendar: HolidayCalendar,
) {
    @Bean
    @StepScope
    fun holidayItemReader(
        @Value("#{jobParameters['businessDate']}") businessDate: String,
    ): ItemReader<SendTarget> {
        val date = LocalDate.parse(businessDate)
        // 발송일이 아니면(공휴일 매칭 없음) 아무 것도 읽지 않는다.
        val holiday = holidayCalendar.holidayToNotifyOn(date)
            ?: return steps.pagingReader { _, _ -> emptyList() }
        return steps.pagingReader { after, size -> reader.readPage(date, holiday.name, after, size) }
    }

    @Bean
    @StepScope
    fun holidayItemProcessor(
        @Value("#{jobParameters['businessDate']}") businessDate: String,
    ): ItemProcessor<SendTarget, PreparedNotification> =
        steps.processor(NotificationType.HOLIDAY_EXPLORE, LocalDate.parse(businessDate))

    @Bean
    fun holidayStep(
        holidayItemReader: ItemReader<SendTarget>,
        holidayItemProcessor: ItemProcessor<SendTarget, PreparedNotification>,
    ): Step = steps.chunkStep(STEP_NAME, holidayItemReader, holidayItemProcessor)

    @Bean(name = [JOB_NAME])
    fun holidayExploreJob(holidayStep: Step): Job = steps.singleStepJob(JOB_NAME, holidayStep)

    companion object {
        const val JOB_NAME = "holidayExploreJob"
        const val STEP_NAME = "holidayStep"
    }
}

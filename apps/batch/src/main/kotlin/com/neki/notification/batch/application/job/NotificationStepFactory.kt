package com.neki.notification.batch.application.job

import com.neki.notification.application.port.out.NotificationLogStore
import com.neki.notification.application.port.out.PushSender
import com.neki.notification.batch.application.step.NotificationItemProcessor
import com.neki.notification.batch.application.step.NotificationItemWriter
import com.neki.notification.batch.application.step.PagingSendTargetItemReader
import com.neki.notification.domain.model.NotificationType
import com.neki.notification.domain.model.PreparedNotification
import com.neki.notification.domain.model.SendTarget
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemReader
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDate

@Component
class NotificationStepFactory(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    pushSender: PushSender,
    private val logStore: NotificationLogStore,
) {
    private val writer = NotificationItemWriter(pushSender, logStore)

    fun pagingReader(fetch: (afterUserId: Long, pageSize: Int) -> List<SendTarget>): ItemReader<SendTarget> =
        PagingSendTargetItemReader(PAGE_SIZE, fetch)

    fun processor(type: NotificationType, businessDate: LocalDate): ItemProcessor<SendTarget, PreparedNotification> =
        NotificationItemProcessor(type, businessDate, logStore)

    fun chunkStep(
        name: String,
        reader: ItemReader<SendTarget>,
        processor: ItemProcessor<SendTarget, PreparedNotification>,
    ): Step =
        StepBuilder(name, jobRepository)
            .chunk<SendTarget, PreparedNotification>(CHUNK_SIZE, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build()

    fun singleStepJob(name: String, step: Step): Job =
        JobBuilder(name, jobRepository).start(step).build()

    companion object {
        const val CHUNK_SIZE = 100
        const val PAGE_SIZE = 100
    }
}

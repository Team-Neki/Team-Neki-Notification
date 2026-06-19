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

/**
 * 알림 배치 Job 3종의 공통 골격 조립 (batch-design §5).
 *
 * 세 Job(WEEKEND/WEEKLY/HOLIDAY)은 Reader → Processor → Writer 청크 구조가 동일하고
 * Reader 쿼리와 [NotificationType]만 다르다. 동일한 청크/Job 조립 보일러플레이트를 이 한 곳에 모아
 * 타입별 `~Job` 설정 클래스는 자기 Reader·타입만 선언하도록 한다 (OCP: 타입 추가 = 클래스 추가).
 *
 * Writer는 무상태 싱글턴이라 팩토리가 직접 보유한다.
 */
@Component
class NotificationStepFactory(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    pushSender: PushSender,
    private val logStore: NotificationLogStore,
) {
    private val writer = NotificationItemWriter(pushSender, logStore)

    /** keyset 페이징 Reader. [fetch]는 (afterUserId, pageSize) → 페이지. */
    fun pagingReader(fetch: (afterUserId: Long, pageSize: Int) -> List<SendTarget>): ItemReader<SendTarget> =
        PagingSendTargetItemReader(PAGE_SIZE, fetch)

    /** 당일 중복 판정 + 도메인 발송 판정 Processor. */
    fun processor(type: NotificationType, businessDate: LocalDate): ItemProcessor<SendTarget, PreparedNotification> =
        NotificationItemProcessor(type, businessDate, logStore)

    /** Reader → Processor → (공통)Writer 청크 Step. */
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

    /** 단일 Step Job. */
    fun singleStepJob(name: String, step: Step): Job =
        JobBuilder(name, jobRepository).start(step).build()

    companion object {
        const val CHUNK_SIZE = 100
        const val PAGE_SIZE = 100
    }
}

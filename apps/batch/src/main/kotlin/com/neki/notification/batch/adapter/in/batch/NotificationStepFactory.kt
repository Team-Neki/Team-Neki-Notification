package com.neki.notification.batch.adapter.`in`.batch

import com.neki.notification.application.port.out.SendTargetReader
import com.neki.notification.batch.application.NotificationSendService
import com.neki.notification.domain.model.NotificationType
import com.neki.notification.domain.model.PreparedNotification
import com.neki.notification.domain.model.SendTarget
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemReader
import org.springframework.batch.item.ItemWriter
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDate

/**
 * 알림 배치 청크 스텝 기계 일체(batch-design §5). 세 Job(WEEKEND/WEEKLY/HOLIDAY)은 Reader →
 * Processor → Writer 청크 구조가 동일하고 Reader 쿼리와 [NotificationType]만 다르다. 동일한
 * 청크/Job 조립 보일러플레이트를 한 곳에 모아 타입별 `~Job` 클래스는 자기 Reader·타입만 선언하게
 * 한다(OCP: 타입 추가 = Job 파일 추가).
 *
 * 이 파일은 "청크 스텝을 어떻게 조립·구동하는가" 하나의 관심사를 담는다: 팩토리(공개 API) +
 * 그 팩토리만 생성하는 세 배치 컴포넌트(Reader/Processor/Writer, 모두 `private` 구현 세부).
 * 실질 로직은 [NotificationSendService]에 위임하고, 팩토리는 청크 경계(CHUNK_SIZE=1)·트랜잭션·
 * Job 배선만 담당한다. Writer는 무상태 싱글턴이라 팩토리가 직접 보유.
 */
@Component
class NotificationStepFactory(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val sendService: NotificationSendService,
) {
    private val writer = NotificationItemWriter(sendService)

    fun pagingReader(reader: SendTargetReader): ItemReader<SendTarget> =
        PagingSendTargetItemReader(PAGE_SIZE, reader)

    fun processor(type: NotificationType, businessDate: LocalDate): ItemProcessor<SendTarget, PreparedNotification> =
        NotificationItemProcessor(type, businessDate, sendService)

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
        const val CHUNK_SIZE = 1 // 건별 트랜잭션 (B-5: docs/lld/code-notes.md 참조)
        const val PAGE_SIZE = 100
    }
}

/**
 * keyset 페이징 `ItemReader`(batch-design §5 Reader). [SendTargetReader] 포트에서 `user_id`
 * 오름차순 페이지를 당겨 1건씩 흘려보낸다. 빈 페이지를 만나면 소진으로 보고 종료.
 * 단일 인스턴스·단일 스레드 Step 전제(분산 락 불필요, batch-design §3).
 */
private class PagingSendTargetItemReader(
    private val pageSize: Int,
    private val reader: SendTargetReader,
) : ItemReader<SendTarget> {

    private val buffer = ArrayDeque<SendTarget>()
    private var lastUserId = 0L
    private var exhausted = false

    override fun read(): SendTarget? {
        if (buffer.isEmpty() && !exhausted) {
            val page = reader.readPage(lastUserId, pageSize)
            if (page.isEmpty()) {
                exhausted = true
            } else {
                buffer.addAll(page)
                lastUserId = page.last().userId
                if (page.size < pageSize) exhausted = true
            }
        }
        return buffer.removeFirstOrNull()
    }
}

/**
 * 배치 Processor 어댑터(batch-design §5). 실질 판정은 [NotificationSendService.prepare]에 위임한다.
 * Skip(미동의/중복)이면 null을 반환해 청크에서 필터된다.
 */
private class NotificationItemProcessor(
    private val type: NotificationType,
    private val businessDate: LocalDate,
    private val sendService: NotificationSendService,
) : ItemProcessor<SendTarget, PreparedNotification> {

    override fun process(item: SendTarget): PreparedNotification? =
        sendService.prepare(item, type, businessDate)
}

/**
 * 배치 Writer 어댑터(batch-design §5 Composite Writer). 청크의 각 건을
 * [NotificationSendService.dispatch]로 흘려보낸다(발송+적재). 건별 트랜잭션 경계(CHUNK_SIZE=1,
 * B-5/M-3)는 스텝 조립([NotificationStepFactory])이 소유한다.
 */
private class NotificationItemWriter(
    private val sendService: NotificationSendService,
) : ItemWriter<PreparedNotification> {

    override fun write(chunk: Chunk<out PreparedNotification>) {
        for (prepared in chunk) {
            sendService.dispatch(prepared)
        }
    }
}

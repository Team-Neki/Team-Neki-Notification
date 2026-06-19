package com.neki.notification.batch.application.step

import com.neki.notification.domain.model.SendTarget
import org.springframework.batch.item.ItemReader

/**
 * keyset 페이징 [ItemReader] (batch-design §5 Reader).
 *
 * `user_id` 오름차순으로 페이지를 당겨 1건씩 흘려보낸다. 빈 페이지를 만나면 소진으로 보고 종료.
 * 단일 인스턴스·단일 스레드 Step 전제 (분산 락 불필요, batch-design §3).
 */
class PagingSendTargetItemReader(
    private val pageSize: Int,
    private val fetch: (afterUserId: Long, pageSize: Int) -> List<SendTarget>,
) : ItemReader<SendTarget> {

    private val buffer = ArrayDeque<SendTarget>()
    private var lastUserId = 0L
    private var exhausted = false

    override fun read(): SendTarget? {
        if (buffer.isEmpty() && !exhausted) {
            val page = fetch(lastUserId, pageSize)
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

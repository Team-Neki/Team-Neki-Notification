package com.neki.notification.batch.application.step

import com.neki.notification.domain.model.SendTarget
import org.springframework.batch.item.ItemReader

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

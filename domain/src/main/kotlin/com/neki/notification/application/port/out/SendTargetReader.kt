package com.neki.notification.application.port.out

import com.neki.notification.domain.model.SendTarget

/**
 * 발송 대상 조회 포트(batch-design §5 Reader). keyset 페이징으로 대상을 한 페이지씩 읽는다.
 *
 * 실행(발송일·유형)에 종속된 파라미터(businessDate·공휴일명 등)는 이 포트에 노출하지 않는다.
 * 그 바인딩은 인바운드 어댑터(배치 Job 조립)가 담당하고, 이 포트는 커서·페이지 크기만 받는
 * "바인딩된 페이지 조회자" 형태다. infra의 유형별 리더(adapter/out/read)가 이 포트를 구현·바인딩한다.
 */
fun interface SendTargetReader {
    /** afterUserId 이후 최대 pageSize건. 소진 시 빈 리스트. */
    fun readPage(afterUserId: Long, pageSize: Int): List<SendTarget>
}

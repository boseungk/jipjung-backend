package com.jipjung.project.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 아파트 데이터 비동기 워밍 서비스
 * 요청 응답을 블로킹하지 않고 MOLIT 동기화를 트리거합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApartmentWarmupService {

    private final ApartmentSyncService syncService;

    @Async("molitWarmupExecutor")
    public void warmupIfMissing(String lawdCd, String dealYmd) {
        // TODO: 트래픽 증가 시 큐 기반 워밍(예: Redis/RabbitMQ)으로 전환 고려.
        log.info("[Warmup] 비동기 동기화 트리거: lawdCd={}, dealYmd={}", lawdCd, dealYmd);
        syncService.fetchAndCacheIfMissing(lawdCd, dealYmd);
    }
}

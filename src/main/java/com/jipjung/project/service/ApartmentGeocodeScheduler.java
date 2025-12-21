package com.jipjung.project.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Coordinate backfill scheduler
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "kakao.api.backfill-enabled", havingValue = "true")
public class ApartmentGeocodeScheduler {

    private final ApartmentGeocodingService geocodingService;

    @Value("${kakao.api.backfill-batch-size:50}")
    private int batchSize;

    @Scheduled(cron = "${kakao.api.backfill-cron:0 */30 * * * *}")
    public void backfillMissingCoordinates() {
        int updated = geocodingService.backfillMissingCoordinates(batchSize);
        if (updated > 0) {
            log.info("[Geo] Scheduler backfill completed: updated={}", updated);
        }
    }
}

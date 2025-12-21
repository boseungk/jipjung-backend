package com.jipjung.project.service;

import com.jipjung.project.domain.Apartment;
import com.jipjung.project.domain.ApartmentDeal;
import com.jipjung.project.external.molit.MolitApiClient;
import com.jipjung.project.external.molit.MolitDealResponse;
import com.jipjung.project.repository.ApartmentDealMapper;
import com.jipjung.project.repository.ApartmentMapper;
import com.jipjung.project.repository.MolitSyncHistoryMapper;
import com.jipjung.project.service.dto.SyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 아파트 데이터 동기화 서비스
 * 국토부 실거래가 API 연동 담당
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApartmentSyncService {

    private final MolitApiClient molitApiClient;
    private final ApartmentMapper apartmentMapper;
    private final ApartmentDealMapper apartmentDealMapper;
    private final MolitSyncHistoryMapper syncHistoryMapper;
    private final ApartmentGeocodingService apartmentGeocodingService;

    @Value("${molit.api.sync-cooldown-hours:24}")
    private int syncCooldownHours;

    @Value("${molit.api.fallback-enabled:true}")
    private boolean fallbackEnabled;

    @Value("${molit.api.admin-enabled:true}")
    private boolean adminEnabled;

    private static final String DEFAULT_LAWD_CD = "11680";  // 강남구

    /**
     * 초기 동기화 (관리자 수동 트리거)
     * 강남구 + 현재월 기준 동기화
     */
    @Transactional
    public SyncResult initialSync() {
        if (!adminEnabled) {
            log.warn("[Sync] 관리자 동기화 비활성화됨");
            return SyncResult.skipped(DEFAULT_LAWD_CD, getCurrentYearMonth(), "관리자 동기화 비활성화됨");
        }
        
        String dealYmd = getCurrentYearMonth();
        return syncRegionMonth(DEFAULT_LAWD_CD, dealYmd);
    }

    /**
     * 특정 지역/년월 동기화 (관리자 수동 트리거)
     */
    @Transactional
    public SyncResult syncByRegion(String lawdCd, String dealYmd) {
        if (!adminEnabled) {
            log.warn("[Sync] 관리자 동기화 비활성화됨");
            return SyncResult.skipped(lawdCd, dealYmd, "관리자 동기화 비활성화됨");
        }
        
        return syncRegionMonth(lawdCd, dealYmd);
    }

    /**
     * Fallback 동기화 (자동 호출)
     * DB에 데이터가 없을 때만 API 호출
     * 24시간 내 동기화 이력 있으면 스킵
     *
     * @return 동기화 수행 여부
     */
    @Transactional
    public boolean fetchAndCacheIfMissing(String lawdCd, String dealYmd) {
        if (!fallbackEnabled) {
            log.debug("[Sync] Fallback 비활성화됨 (lawdCd={}, dealYmd={})", lawdCd, dealYmd);
            return false;
        }

        // 24시간 내 동기화 이력 체크
        LocalDateTime cutoff = LocalDateTime.now().minusHours(syncCooldownHours);
        if (syncHistoryMapper.existsRecentSync(lawdCd, dealYmd, cutoff)) {
            log.info("[Sync] 최근 동기화됨 - 스킵 (lawdCd={}, dealYmd={})", lawdCd, dealYmd);
            return false;
        }

        log.info("[Sync] Fallback 동기화 시작 (lawdCd={}, dealYmd={})", lawdCd, dealYmd);
        SyncResult result = syncRegionMonth(lawdCd, dealYmd);
        return result.syncedCount() > 0;
    }

    /**
     * Fallback 활성화 여부
     */
    public boolean isFallbackEnabled() {
        return fallbackEnabled;
    }

    /**
     * 핵심 동기화 로직
     */
    private SyncResult syncRegionMonth(String lawdCd, String dealYmd) {
        try {
            // 1. API 호출
            List<MolitDealResponse> deals = molitApiClient.fetchDeals(lawdCd, dealYmd);

            if (deals.isEmpty()) {
                log.info("[Sync] API 응답 없음 (lawdCd={}, dealYmd={})", lawdCd, dealYmd);
                syncHistoryMapper.insertOrUpdate(lawdCd, dealYmd, 0);
                return SyncResult.success(lawdCd, dealYmd, 0, 0);
            }

            // 2. 저장
            int syncedCount = 0;
            int skippedCount = 0;
            Set<String> geocodeChecked = new HashSet<>();

            for (MolitDealResponse deal : deals) {
                // 해제 거래 스킵
                if (deal.isCanceledDeal()) {
                    log.debug("[Sync] 해제 거래 스킵: aptNm={}", deal.getAptNm());
                    skippedCount++;
                    continue;
                }

                try {
                    boolean saved = saveDeal(deal, lawdCd, geocodeChecked);
                    if (saved) {
                        syncedCount++;
                    } else {
                        skippedCount++;
                    }
                } catch (Exception e) {
                    log.warn("[Sync] 개별 저장 실패: aptNm={}, error={}", deal.getAptNm(), e.getMessage());
                    skippedCount++;
                }
            }

            // 3. 이력 기록
            syncHistoryMapper.insertOrUpdate(lawdCd, dealYmd, syncedCount);
            
            log.info("[Sync] 완료: lawdCd={}, dealYmd={}, synced={}, skipped={}",
                    lawdCd, dealYmd, syncedCount, skippedCount);
            
            return SyncResult.success(lawdCd, dealYmd, syncedCount, skippedCount);

        } catch (Exception e) {
            log.error("[Sync] 동기화 실패: lawdCd={}, dealYmd={}, error={}",
                    lawdCd, dealYmd, e.getMessage(), e);
            return SyncResult.error(lawdCd, dealYmd, e.getMessage());
        }
    }

    /**
     * 개별 거래 저장 (아파트 upsert + 거래 insert)
     *
     * @return true if saved, false if duplicate
     */
    private boolean saveDeal(MolitDealResponse deal, String lawdCd, Set<String> geocodeChecked) {
        NormalizedDeal normalized = normalizeDeal(deal, lawdCd);
        if (!normalized.isValid()) {
            log.warn("[Sync] 필수 값 누락 - 스킵: lawdCd={}, aptNm={}, dealAmount={}",
                    lawdCd, normalized.aptNm(), normalized.dealAmount());
            return false;
        }

        // 1. apt_seq 생성
        String aptSeq = generateAptSeq(
                normalized.sggCd(),
                normalized.umdNm(),
                normalized.aptNm(),
                normalized.jibun()
        );

        // 2. 아파트 Upsert
        Apartment apartment = Apartment.builder()
                .aptSeq(aptSeq)
                .sggCd(normalized.sggCd())
                .umdNm(normalized.umdNm())
                .aptNm(normalized.aptNm())
                .jibun(normalized.jibun())
                .buildYear(normalized.buildYear())
                .build();

        apartmentMapper.upsert(apartment);

        if (geocodeChecked.add(aptSeq)) {
            apartmentGeocodingService.updateCoordinatesIfMissing(
                    aptSeq,
                    normalized.sggCd(),
                    normalized.umdNm(),
                    normalized.jibun(),
                    normalized.aptNm()
            );
        }

        // 3. 거래 Insert (중복은 DB 유니크 + INSERT IGNORE로 무시)
        ApartmentDeal dealEntity = ApartmentDeal.builder()
                .aptSeq(aptSeq)
                .aptDong(normalized.aptDong())
                .floor(normalized.floor())
                .dealYear(normalized.dealYear())
                .dealMonth(normalized.dealMonth())
                .dealDay(normalized.dealDay())
                .excluUseAr(normalized.excluUseAr())
                .dealAmount(normalized.dealAmount())
                .build();

        int inserted = apartmentDealMapper.insert(dealEntity);
        if (inserted == 0) {
            log.debug("[Sync] 중복 거래 스킵: aptSeq={}", aptSeq);
            return false;
        }
        return true;
    }

    /**
     * apt_seq 생성
     * 형식: {sggCd}-{sha256(umdNm|aptNm|jibun).substring(0,12)}
     * 20자 VARCHAR 제약 충족
     */
    private String generateAptSeq(String sggCd, String umdNm, String aptNm, String jibun) {
        String combined = String.format("%s|%s|%s", 
                normalizeToEmpty(umdNm), 
                normalizeToEmpty(aptNm), 
                normalizeToEmpty(jibun));
        String hash = DigestUtils.sha256Hex(combined).substring(0, 12);
        return sggCd + "-" + hash;
    }

    /**
     * 거래 데이터 정규화
     */
    private NormalizedDeal normalizeDeal(MolitDealResponse deal, String lawdCd) {
        String sggCd = normalizeToNull(deal.getSggCd());
        if (sggCd == null) {
            sggCd = normalizeToNull(lawdCd);
        }
        return new NormalizedDeal(
                sggCd,
                normalizeToNull(deal.getUmdNm()),
                normalizeToNull(deal.getAptNm()),
                normalizeToNull(deal.getJibun()),
                normalizeToNull(deal.getAptDong()),
                normalizeToNull(deal.getFloor()),
                deal.getDealYearInt(),
                deal.getDealMonthInt(),
                deal.getDealDayInt(),
                deal.getExcluUseArDecimal(),
                normalizeToNull(deal.getDealAmountNormalized()),
                deal.getBuildYearInt()
        );
    }

    private String getCurrentYearMonth() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    private String normalizeToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeToEmpty(String value) {
        String normalized = normalizeToNull(value);
        return normalized != null ? normalized : "";
    }

    private record NormalizedDeal(
            String sggCd,
            String umdNm,
            String aptNm,
            String jibun,
            String aptDong,
            String floor,
            Integer dealYear,
            Integer dealMonth,
            Integer dealDay,
            java.math.BigDecimal excluUseAr,
            String dealAmount,
            Integer buildYear
    ) {
        private boolean isValid() {
            return sggCd != null
                    && aptNm != null
                    && dealYear != null
                    && dealMonth != null
                    && dealDay != null
                    && floor != null
                    && excluUseAr != null
                    && dealAmount != null;
        }
    }
}

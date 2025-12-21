package com.jipjung.project.controller;

import com.jipjung.project.global.response.ApiResponse;
import com.jipjung.project.service.ApartmentGeocodingService;
import com.jipjung.project.service.ApartmentSyncService;
import com.jipjung.project.service.dto.SyncResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자 동기화 API
 * MOLIT 실거래 데이터 수동 동기화
 */
@RestController
@RequestMapping("/api/admin/sync")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Sync", description = "관리자 데이터 동기화 API")
public class AdminSyncController {

    private final ApartmentSyncService syncService;
    private final ApartmentGeocodingService geocodingService;

    /**
     * 초기 동기화 (강남구 + 현재월)
     * POST /api/admin/sync/initial
     */
    @Operation(summary = "초기 동기화", description = "강남구 현재월 데이터를 MOLIT API에서 동기화")
    @PostMapping("/initial")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SyncResult>> triggerInitialSync(
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.info("[Admin Sync] 초기 동기화 요청 by {}", 
                userDetails != null ? userDetails.getUsername() : "unknown");
        
        SyncResult result = syncService.initialSync();
        return ApiResponse.success(result);
    }

    /**
     * 특정 지역/년월 동기화
     * POST /api/admin/sync/region?lawdCd=11680&dealYmd=202412
     */
    @Operation(summary = "지역별 동기화", description = "특정 지역/년월 데이터를 MOLIT API에서 동기화")
    @PostMapping("/region")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SyncResult>> syncByRegion(
            @Parameter(description = "법정동코드 앞 5자리", example = "11680")
            @RequestParam String lawdCd,
            @Parameter(description = "거래년월 (YYYYMM)", example = "202412")
            @RequestParam String dealYmd,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        log.info("[Admin Sync] 지역 동기화 요청 by {} - lawdCd={}, dealYmd={}", 
                userDetails != null ? userDetails.getUsername() : "unknown", lawdCd, dealYmd);
        
        SyncResult result = syncService.syncByRegion(lawdCd, dealYmd);
        return ApiResponse.success(result);
    }

    /**
     * 동기화 상태 확인
     * GET /api/admin/sync/status
     */
    @Operation(summary = "동기화 상태 확인", description = "Fallback 활성화 여부 등 상태 확인")
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SyncStatusResponse>> getStatus() {
        return ApiResponse.success(new SyncStatusResponse(
                syncService.isFallbackEnabled()
        ));
    }

    /**
     * 좌표 백필 실행 (좌표 누락 데이터 보정)
     * POST /api/admin/sync/geocode/backfill?limit=50
     */
    @Operation(summary = "좌표 백필", description = "좌표가 없는 아파트에 대해 지오코딩을 수행합니다.")
    @PostMapping("/geocode/backfill")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<GeocodeBackfillResponse>> backfillGeocode(
            @Parameter(description = "최대 처리 개수", example = "50")
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("[Admin Geo] 좌표 백필 요청 by {} - limit={}",
                userDetails != null ? userDetails.getUsername() : "unknown", limit);
        int updated = geocodingService.backfillMissingCoordinates(limit);
        return ApiResponse.success(new GeocodeBackfillResponse(updated));
    }

    public record SyncStatusResponse(boolean fallbackEnabled) {}
    public record GeocodeBackfillResponse(int updated) {}
}

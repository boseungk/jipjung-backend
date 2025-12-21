package com.jipjung.project.service;

import com.jipjung.project.domain.Apartment;
import com.jipjung.project.external.kakao.KakaoGeoClient;
import com.jipjung.project.repository.ApartmentMapper;
import com.jipjung.project.repository.DongcodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Apartment geocoding service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApartmentGeocodingService {

    private final KakaoGeoClient kakaoGeoClient;
    private final ApartmentMapper apartmentMapper;
    private final DongcodeMapper dongcodeMapper;

    @Transactional
    public boolean updateCoordinatesIfMissing(
            String aptSeq,
            String sggCd,
            String umdNm,
            String jibun,
            String aptNm
    ) {
        Optional<Apartment> existing = apartmentMapper.findByAptSeq(aptSeq);
        if (existing.isPresent()) {
            Apartment apartment = existing.get();
            if (apartment.getLatitude() != null && apartment.getLongitude() != null) {
                return false;
            }
        }

        String regionPrefix = resolveRegionPrefix(sggCd, umdNm);
        if (regionPrefix == null || regionPrefix.isBlank()) {
            log.warn("[Geo] Failed to resolve region prefix: sggCd={}, umdNm={}", sggCd, umdNm);
            return false;
        }

        String baseAddress = buildAddress(regionPrefix, umdNm, jibun);
        Optional<KakaoGeoClient.KakaoCoordinates> coords = kakaoGeoClient.geocode(baseAddress);
        if (coords.isEmpty() && aptNm != null && !aptNm.isBlank()) {
            String fallbackAddress = buildAddress(regionPrefix, umdNm, aptNm);
            coords = kakaoGeoClient.geocode(fallbackAddress);
        }

        if (coords.isEmpty()) {
            return false;
        }

        String dongCode = resolveDongCode(sggCd, umdNm);
        KakaoGeoClient.KakaoCoordinates value = coords.get();
        apartmentMapper.updateLocation(aptSeq, dongCode, value.latitude(), value.longitude());
        return true;
    }

    @Transactional
    public int backfillMissingCoordinates(int limit) {
        int batchLimit = Math.max(1, limit);
        List<Apartment> targets = apartmentMapper.findMissingCoordinates(batchLimit);
        int updated = 0;
        for (Apartment apartment : targets) {
            boolean changed = updateCoordinatesIfMissing(
                    apartment.getAptSeq(),
                    apartment.getSggCd(),
                    apartment.getUmdNm(),
                    apartment.getJibun(),
                    apartment.getAptNm()
            );
            if (changed) {
                updated++;
            }
        }
        if (updated > 0) {
            log.info("[Geo] Backfill completed: updated={}/{}", updated, targets.size());
        }
        return updated;
    }

    private String resolveRegionPrefix(String sggCd, String umdNm) {
        if (sggCd == null || sggCd.isBlank()) {
            return null;
        }
        String resolved = dongcodeMapper.findRegionPrefixBySggCdAndUmdNm(sggCd, umdNm);
        if (resolved != null && !resolved.isBlank()) {
            return resolved;
        }
        return dongcodeMapper.findRegionPrefixBySggCd(sggCd);
    }

    private String resolveDongCode(String sggCd, String umdNm) {
        if (sggCd == null || sggCd.isBlank() || umdNm == null || umdNm.isBlank()) {
            return null;
        }
        return dongcodeMapper.findDongCodeBySggCdAndUmdNm(sggCd, umdNm);
    }

    private String buildAddress(String regionPrefix, String umdNm, String detail) {
        StringBuilder builder = new StringBuilder();
        if (regionPrefix != null && !regionPrefix.isBlank()) {
            builder.append(regionPrefix.trim());
        }
        if (umdNm != null && !umdNm.isBlank()) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(umdNm.trim());
        }
        if (detail != null && !detail.isBlank()) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(detail.trim());
        }
        return builder.toString();
    }
}

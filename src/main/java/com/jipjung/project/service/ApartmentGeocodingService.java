package com.jipjung.project.service;

import com.jipjung.project.domain.Apartment;
import com.jipjung.project.external.kakao.KakaoGeoClient;
import com.jipjung.project.repository.ApartmentMapper;
import com.jipjung.project.repository.DongcodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Apartment geocoding service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApartmentGeocodingService {

    private static final int MIN_BATCH_LIMIT = 1;

    private final KakaoGeoClient kakaoGeoClient;
    private final ApartmentMapper apartmentMapper;
    private final DongcodeMapper dongcodeMapper;

    public boolean updateCoordinatesIfMissing(
            String aptSeq,
            String sggCd,
            String umdNm,
            String jibun,
            String aptNm
    ) {
        if (isBlank(aptSeq)) {
            log.warn("[Geo] Missing aptSeq for geocoding request");
            return false;
        }

        Optional<Apartment> apartment = findApartment(aptSeq);
        if (apartment.isEmpty()) {
            return false;
        }
        if (hasCoordinates(apartment.get())) {
            return false;
        }

        String regionPrefix = resolveRegionPrefix(sggCd, umdNm);
        if (isBlank(regionPrefix)) {
            log.warn("[Geo] Failed to resolve region prefix: sggCd={}, umdNm={}", sggCd, umdNm);
            return false;
        }

        List<String> addressCandidates = buildAddressCandidates(regionPrefix, umdNm, jibun, aptNm);
        Optional<KakaoGeoClient.KakaoCoordinates> coords = geocodeFirst(addressCandidates, aptSeq);
        if (coords.isEmpty()) {
            return false;
        }

        String dongCode = resolveDongCode(sggCd, umdNm);
        KakaoGeoClient.KakaoCoordinates value = coords.get();
        return updateCoordinates(aptSeq, dongCode, value);
    }

    public int backfillMissingCoordinates(int limit) {
        int batchLimit = Math.max(MIN_BATCH_LIMIT, limit);
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

    private Optional<Apartment> findApartment(String aptSeq) {
        Optional<Apartment> apartment = apartmentMapper.findByAptSeq(aptSeq);
        if (apartment.isEmpty()) {
            log.warn("[Geo] Apartment not found for geocoding: aptSeq={}", aptSeq);
        }
        return apartment;
    }

    private boolean hasCoordinates(Apartment apartment) {
        return apartment.getLatitude() != null && apartment.getLongitude() != null;
    }

    private String resolveRegionPrefix(String sggCd, String umdNm) {
        if (isBlank(sggCd)) {
            return null;
        }
        String resolved = dongcodeMapper.findRegionPrefixBySggCdAndUmdNm(sggCd, umdNm);
        if (!isBlank(resolved)) {
            return resolved;
        }
        return dongcodeMapper.findRegionPrefixBySggCd(sggCd);
    }

    private String resolveDongCode(String sggCd, String umdNm) {
        if (isBlank(sggCd) || isBlank(umdNm)) {
            return null;
        }
        return dongcodeMapper.findDongCodeBySggCdAndUmdNm(sggCd, umdNm);
    }

    private List<String> buildAddressCandidates(String regionPrefix, String umdNm, String jibun, String aptNm) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, buildAddress(regionPrefix, umdNm, jibun));
        addCandidate(candidates, buildAddress(regionPrefix, umdNm, aptNm));
        return List.copyOf(candidates);
    }

    private void addCandidate(Set<String> candidates, String address) {
        if (!isBlank(address)) {
            candidates.add(address);
        }
    }

    private Optional<KakaoGeoClient.KakaoCoordinates> geocodeFirst(List<String> addresses, String aptSeq) {
        for (String address : addresses) {
            Optional<KakaoGeoClient.KakaoCoordinates> coords = kakaoGeoClient.geocode(address);
            if (coords.isPresent()) {
                return coords;
            }
        }
        if (!addresses.isEmpty()) {
            log.info("[Geo] Geocoding failed: aptSeq={}, addresses={}", aptSeq, String.join(" | ", addresses));
        } else {
            log.info("[Geo] Geocoding failed: aptSeq={}, no address candidates", aptSeq);
        }
        return Optional.empty();
    }

    private boolean updateCoordinates(String aptSeq, String dongCode, KakaoGeoClient.KakaoCoordinates coords) {
        int updated = apartmentMapper.updateLocationIfMissing(
                aptSeq,
                dongCode,
                coords.latitude(),
                coords.longitude()
        );
        if (updated == 0) {
            log.debug("[Geo] Coordinates already set or apartment missing: aptSeq={}", aptSeq);
            return false;
        }
        return true;
    }

    private String buildAddress(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (isBlank(part)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(part.trim());
        }
        return builder.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

package com.jipjung.project.external.kakao;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Kakao Local API geocoding client
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KakaoGeoClient {

    @Value("${kakao.api.rest-key:}")
    private String restKey;

    @Value("${kakao.api.base-url}")
    private String baseUrl;

    @Value("${kakao.api.enabled:true}")
    private boolean enabled;

    @Value("${kakao.api.rate-limit-ms:120}")
    private long rateLimitMs;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @PostConstruct
    void logKeyStatus() {
        if (restKey == null || restKey.isBlank()) {
            log.warn("[Kakao Geo] REST API key is missing. Check KAKAO_REST_API_KEY.");
            return;
        }
        log.info("[Kakao Geo] REST API key loaded (len={}, enabled={})", restKey.length(), enabled);
    }

    public Optional<KakaoCoordinates> geocode(String address) {
        if (!enabled) {
            log.debug("[Kakao Geo] Disabled - skip (address={})", address);
            return Optional.empty();
        }
        if (restKey == null || restKey.isBlank()) {
            log.warn("[Kakao Geo] REST API key is missing. Geocoding skipped.");
            return Optional.empty();
        }
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }

        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .queryParam("query", address)
                    .queryParam("size", 1)
                    .encode(StandardCharsets.UTF_8)
                    .build()
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + restKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
            String body = response.getBody();
            if (body == null || body.isBlank()) {
                log.warn("[Kakao Geo] Empty response (address={})", address);
                return Optional.empty();
            }

            KakaoAddressResponse parsed = objectMapper.readValue(body, KakaoAddressResponse.class);
            List<KakaoAddressResponse.Document> documents = parsed.getDocuments();
            if (documents == null || documents.isEmpty()) {
                log.info("[Kakao Geo] No geocode result (address={})", address);
                return Optional.empty();
            }

            KakaoAddressResponse.Document doc = documents.get(0);
            BigDecimal longitude = parseDecimal(doc.getX());
            BigDecimal latitude = parseDecimal(doc.getY());
            if (latitude == null || longitude == null) {
                log.warn("[Kakao Geo] Failed to parse coordinates (address={})", address);
                return Optional.empty();
            }

            return Optional.of(new KakaoCoordinates(latitude, longitude));
        } catch (Exception e) {
            log.warn("[Kakao Geo] Geocoding failed (address={}, error={})", address, e.getMessage());
            return Optional.empty();
        } finally {
            throttle();
        }
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value.trim());
    }

    private void throttle() {
        if (rateLimitMs <= 0) {
            return;
        }
        try {
            Thread.sleep(rateLimitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record KakaoCoordinates(BigDecimal latitude, BigDecimal longitude) {}
}

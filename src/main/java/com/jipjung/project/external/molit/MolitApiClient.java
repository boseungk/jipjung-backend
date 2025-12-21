package com.jipjung.project.external.molit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import jakarta.annotation.PostConstruct;

/**
 * 국토부 실거래가 API 클라이언트
 * 페이징 처리 및 Rate Limiting 적용
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MolitApiClient {

    private static final int DEFAULT_NUM_OF_ROWS = 100;
    private static final long RATE_LIMIT_DELAY_MS = 100;  // 초당 10회 제한 대응

    @Value("${molit.api.key}")
    private String apiKey;

    @Value("${molit.api.base-url}")
    private String baseUrl;

    @Value("${molit.api.key-encoded:true}")
    private boolean keyEncoded;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @PostConstruct
    void logKeyStatus() {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("[MOLIT API] serviceKey가 비어있습니다. molit.api.key 설정을 확인하세요.");
            return;
        }
        log.info("[MOLIT API] serviceKey 로드됨 (len={}, encoded={})", apiKey.length(), keyEncoded);
    }

    /**
     * 지역/년월 기준 실거래 데이터 조회
     * 자동 페이징으로 전체 데이터 수집
     *
     * @param lawdCd  법정동코드 앞 5자리 (예: 11680 = 강남구)
     * @param dealYmd 거래년월 (YYYYMM)
     * @return 전체 거래 데이터 리스트
     */
    public List<MolitDealResponse> fetchDeals(String lawdCd, String dealYmd) {
        List<MolitDealResponse> allDeals = new ArrayList<>();
        int pageNo = 1;

        log.info("[MOLIT API] 호출 시작: lawdCd={}, dealYmd={}", lawdCd, dealYmd);

        while (true) {
            try {
                URI uri = buildUri(lawdCd, dealYmd, pageNo, DEFAULT_NUM_OF_ROWS);
                log.info("[MOLIT API] 요청 파라미터: lawdCd={}, dealYmd={}, page={}, rows={}", lawdCd, dealYmd, pageNo, DEFAULT_NUM_OF_ROWS);

                String xml = restTemplate.getForObject(uri, String.class);
                if (xml == null || xml.isBlank()) {
                    log.warn("[MOLIT API] 빈 응답: page={}", pageNo);
                    break;
                }
                MolitApiResponse response = parseXml(xml);

                if (!response.isSuccess()) {
                    log.warn("[MOLIT API] 오류 응답: {}", response.getErrorMessage());
                    break;
                }

                List<MolitDealResponse> items = response.getItems();
                if (items.isEmpty()) {
                    log.info("[MOLIT API] 데이터 없음 (page={})", pageNo);
                    break;
                }

                allDeals.addAll(items);
                log.debug("[MOLIT API] 수집: {} 건 (누적: {})", items.size(), allDeals.size());

                // 마지막 페이지 체크
                if (items.size() < DEFAULT_NUM_OF_ROWS) {
                    break;
                }

                pageNo++;
                Thread.sleep(RATE_LIMIT_DELAY_MS);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[MOLIT API] 중단됨");
                break;
            } catch (Exception e) {
                log.error("[MOLIT API] 호출 실패: {}", e.getMessage(), e);
                break;
            }
        }

        log.info("[MOLIT API] 완료: 총 {} 건 수집", allDeals.size());
        return allDeals;
    }

    /**
     * API 호출이 가능한지 확인 (연결 테스트)
     */
    public boolean isHealthy() {
        try {
            URI uri = buildUri("11680", "202412", 1, 1);
            String xml = restTemplate.getForObject(uri, String.class);
            if (xml == null || xml.isBlank()) {
                return false;
            }
            MolitApiResponse response = parseXml(xml);
            return response.isSuccess();
        } catch (Exception e) {
            log.warn("[MOLIT API] 헬스체크 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * XML 응답 파싱
     */
    private MolitApiResponse parseXml(String xml) throws Exception {
        String sanitized = sanitizeXml(xml);
        if (sanitized.startsWith("{")) {
            return parseJson(sanitized);
        }
        if (!sanitized.startsWith("<")) {
            String snippet = sanitized.substring(0, Math.min(300, sanitized.length()))
                    .replace("\n", " ")
                    .replace("\r", " ")
                    .replace("\t", " ");
            log.error("[MOLIT API] XML 시작 문자가 아님 (snippet=\"{}\")", snippet);
            throw new IllegalStateException("MOLIT API 응답이 XML이 아닙니다.");
        }
        try {
            JAXBContext context = JAXBContext.newInstance(MolitApiResponse.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            return (MolitApiResponse) unmarshaller.unmarshal(new StringReader(sanitized));
        } catch (Exception e) {
            String snippet = sanitized.substring(0, Math.min(300, sanitized.length()))
                    .replace("\n", " ")
                    .replace("\r", " ")
                    .replace("\t", " ");
            log.error("[MOLIT API] XML 파싱 실패 (snippet=\"{}\")", snippet, e);
            throw e;
        }
    }

    private String sanitizeXml(String xml) {
        if (xml == null) {
            return "";
        }
        String trimmed = xml.strip();
        if (!trimmed.isEmpty() && trimmed.charAt(0) == '\uFEFF') {
            trimmed = trimmed.substring(1).strip();
        }
        int firstTag = trimmed.indexOf('<');
        if (firstTag > 0) {
            String prefix = trimmed.substring(0, Math.min(60, firstTag))
                    .replace("\n", " ")
                    .replace("\r", " ")
                    .replace("\t", " ");
            log.warn("[MOLIT API] XML 시작 전 잡음 제거: \"{}\"", prefix);
            trimmed = trimmed.substring(firstTag);
        }
        return trimmed;
    }

    private MolitApiResponse parseJson(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode responseNode = root.path("response");
        if (responseNode.isMissingNode() || responseNode.isNull()) {
            log.error("[MOLIT API] JSON response 노드가 없습니다.");
            throw new IllegalStateException("MOLIT API 응답이 예상한 JSON 형식이 아닙니다.");
        }
        return objectMapper.treeToValue(responseNode, MolitApiResponse.class);
    }

    /**
     * API URI 빌드
     * serviceKey를 URL 인코딩하여 특수문자(/, +, =) 처리
     */
    private URI buildUri(String lawdCd, String dealYmd, int pageNo, int numOfRows) {
        String serviceKey = keyEncoded ? apiKey : URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        String url = baseUrl + "?serviceKey=" + serviceKey
                + "&LAWD_CD=" + lawdCd
                + "&DEAL_YMD=" + dealYmd
                + "&pageNo=" + pageNo
                + "&numOfRows=" + numOfRows;
        return URI.create(url);
    }
}

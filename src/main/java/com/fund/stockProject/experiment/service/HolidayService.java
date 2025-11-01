package com.fund.stockProject.experiment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class HolidayService {

    private final WebClient.Builder webClientBuilder;
    
    @Value("${api.data.go.kr.service-key:}")
    private String serviceKey;

    private static final String API_BASE_URL = "http://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService";
    
    // 간단한 메모리 캐시 (연도별)
    private final ConcurrentHashMap<Integer, Set<LocalDate>> holidayCache = new ConcurrentHashMap<>();

    /**
     * 특정 연도의 공휴일 목록을 조회합니다. (메모리 캐싱 적용)
     */
    public Mono<Set<LocalDate>> getHolidays(int year) {
        // 캐시 확인
        if (holidayCache.containsKey(year)) {
            return Mono.just(holidayCache.get(year));
        }
        
        log.info("Fetching holidays for year: {}", year);
        
        WebClient webClient = webClientBuilder
                .baseUrl(API_BASE_URL)
                .build();
        
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/getRestDeInfo")
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("solYear", year)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(xmlResponse -> {
                    Set<LocalDate> holidays = parseHolidays(xmlResponse);
                    // 캐시에 저장
                    holidayCache.put(year, holidays);
                    return holidays;
                })
                .doOnError(error -> log.error("Failed to fetch holidays for year: {}", year, error))
                .onErrorReturn(new HashSet<>()); // 에러 발생 시 빈 Set 반환
    }

    /**
     * 특정 날짜가 공휴일인지 확인합니다.
     */
    public Mono<Boolean> isHoliday(LocalDate date) {
        return getHolidays(date.getYear())
                .map(holidays -> holidays.contains(date));
    }

    /**
     * XML 응답을 파싱하여 공휴일 목록을 추출합니다.
     */
    private Set<LocalDate> parseHolidays(String xmlResponse) {
        Set<LocalDate> holidays = new HashSet<>();
        try {
            // XML 파싱 (간단한 파싱 - 필요시 더 정교하게 구현)
            // 응답 형식: <items><item><locdate>20250101</locdate></item>...</items>
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
            
            // XML에서 날짜 추출 (간단한 정규식 방식)
            String datePattern = "<locdate>(\\d{8})</locdate>";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(datePattern);
            java.util.regex.Matcher matcher = pattern.matcher(xmlResponse);
            
            while (matcher.find()) {
                String dateStr = matcher.group(1);
                try {
                    LocalDate holiday = LocalDate.parse(dateStr, formatter);
                    holidays.add(holiday);
                } catch (Exception e) {
                    log.warn("Failed to parse holiday date: {}", dateStr, e);
                }
            }
            
            log.info("Parsed {} holidays from API response", holidays.size());
        } catch (Exception e) {
            log.error("Failed to parse holidays XML", e);
        }
        
        return holidays;
    }

    /**
     * 동기 방식으로 공휴일 체크 (블로킹)
     */
    public boolean isHolidaySync(LocalDate date) {
        try {
            Set<LocalDate> holidays = getHolidays(date.getYear()).block();
            return holidays != null && holidays.contains(date);
        } catch (Exception e) {
            log.error("Failed to check holiday synchronously", e);
            return false; // 에러 시 공휴일이 아닌 것으로 처리
        }
    }
}


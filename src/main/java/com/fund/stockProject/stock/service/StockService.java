package com.fund.stockProject.stock.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fund.stockProject.global.config.SecurityHttpConfig;
import com.fund.stockProject.keyword.entity.Keyword;
import com.fund.stockProject.keyword.entity.StockKeyword;
import com.fund.stockProject.keyword.repository.KeywordRepository;
import com.fund.stockProject.score.entity.Score;
import com.fund.stockProject.score.repository.ScoreRepository;
import com.fund.stockProject.score.service.ScoreService;
import com.fund.stockProject.stock.domain.CATEGORY;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.domain.EXCHANGENUM;
import com.fund.stockProject.stock.domain.DomesticSector;
import com.fund.stockProject.stock.domain.OverseasSector;
import com.fund.stockProject.stock.dto.response.*;
import com.fund.stockProject.stock.dto.response.StockChartResponse.PriceInfo;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.repository.StockQueryRepository;
import com.fund.stockProject.stock.repository.StockRepository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final ScoreRepository scoreRepository;
    private final StockQueryRepository stockQueryRepository;
    private final SecurityService securityService;
    private final ScoreService scoreService;
    private final SecurityHttpConfig securityHttpConfig;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final KeywordRepository keywordRepository;

    private final int LIMITS = 9;

    public Mono<StockInfoResponse> searchStockBySymbolName(final String searchKeyword,
        final String country) {
        List<EXCHANGENUM> koreaExchanges = List.of(EXCHANGENUM.KOSPI, EXCHANGENUM.KOSDAQ,
            EXCHANGENUM.KOREAN_ETF);
        List<EXCHANGENUM> overseaExchanges = List.of(EXCHANGENUM.NAS, EXCHANGENUM.NYS,
            EXCHANGENUM.AMS);

        final Optional<Stock> bySymbolNameAndCountryWithEnums = stockRepository.findBySearchKeywordAndCountryWithEnums(
            searchKeyword, country, koreaExchanges, overseaExchanges);

        if (bySymbolNameAndCountryWithEnums.isPresent()) {
            final Stock stock = bySymbolNameAndCountryWithEnums.get();

            return securityService.getSecurityStockInfoKorea(stock.getId(), stock.getSymbolName(),
                stock.getSecurityName(), stock.getSymbol(), stock.getExchangeNum(),
                getCountryFromExchangeNum(stock.getExchangeNum()));
        }

        return Mono.empty();
    }


    public List<StockSearchResponse> autoCompleteKeyword(String keyword) {
        final List<Stock> stocks = stockQueryRepository.autocompleteKeyword(keyword);

        if (stocks.isEmpty()) {
            return null;
        }

        return stocks.stream()
            .map(stock -> StockSearchResponse.builder()
                .stockId(stock.getId())
                .symbol(stock.getSymbol())
                .symbolName(stock.getSymbolName())
                .securityName(stock.getSecurityName())
                .exchangeNum(stock.getExchangeNum())
                .country(getCountryFromExchangeNum(stock.getExchangeNum()))
                .build())
            .collect(Collectors.toList());
    }

    /**
     * API 호출을 통해 symbol_name을 가져옵니다.
     *
     * @param symbol      종목 심볼
     * @param exchangeNum 거래소 코드 (String 타입)
     * @return API에서 가져온 symbol_name (prdt_name 값)
     */
    public String fetchSymbolName(String symbol, EXCHANGENUM exchangeNum) {
        try {
            HttpHeaders headers = securityHttpConfig.createSecurityHeaders();
            headers.set("tr_id", "CTPF1702R");

            String response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/uapi/overseas-price/v1/quotations/search-info")
                    .queryParam("PDNO", symbol)
                    .queryParam("PRDT_TYPE_CD", String.valueOf(exchangeNum))
                    .build())
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            // JSON 파싱
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode outputNode = rootNode.path("output");

            return outputNode.path("prdt_name").asText(null);
        } catch (Exception e) {
            System.err.println("Error while fetching symbol name for symbol: " + symbol);
            e.printStackTrace();

            return null;
        }
    }

    /**
     * 국내/해외 HOT 지표 반환
     * 현재는 거래량으로 처리
     * @param country 국내/해외 분류
     * @return 점수 정보
     */
    public Mono<List<StockSimpleResponse>> getHotStocks(COUNTRY country) {
        if (country == COUNTRY.KOREA) {
            return securityService.getVolumeRankKorea()
                .map(volumeRankResponses -> volumeRankResponses.stream()
                    .map(rankResponse -> stockRepository.findStockBySymbolNameWithScores(
                        rankResponse.getHtsKorIsnm()).orElse(null))
                    .filter(Objects::nonNull) // null인 경우 건너뜀
                    .map(stock -> {
                        // 첫 인간지표인 경우, 점수 계산 실행
                        LocalDate initialDate = LocalDate.of(1111, 11, 11);
                        if (stock.getScores().get(0).getDate().isEqual(initialDate)) {
                            int newScore = scoreService.getScoreById(stock.getId(), country)
                                .getScore();
                            // 첫 Score의 점수를 갱신
                            stock.getScores().get(0).setScoreKorea(newScore);
                        }

                        return StockSimpleResponse.builder()
                            .stockId(stock.getId())
                            .symbolName(stock.getSymbolName())
                            .score(stock.getScores().get(0).getScoreKorea()) // 최신 데이터를 보장
                            .diff(stock.getScores().get(0).getDiff())
                            .build();
                    })
                    .collect(Collectors.toList()));
        } else if (country == COUNTRY.OVERSEA) {
            return securityService.getVolumeRankOversea()
                .map(volumeRankResponses -> volumeRankResponses.stream()
                    .map(rankResponse -> stockRepository.findStockBySymbolWithScores(
                        rankResponse.getSymb()).orElse(null))
                    .filter(Objects::nonNull) // null인 경우 건너뜀
                    .map(stock -> {
                        // 첫 인간지표인 경우, 점수 계산 실행
                        LocalDate initialDate = LocalDate.of(1111, 11, 11);
                        if (stock.getScores().get(0).getDate().isEqual(initialDate)) {
                            int newScore = scoreService.getScoreById(stock.getId(), country)
                                .getScore();
                            // 첫 Score의 점수를 갱신
                            stock.getScores().get(0).setScoreOversea(newScore);
                        }

                        return StockSimpleResponse.builder()
                            .stockId(stock.getId())
                            .symbolName(stock.getSymbolName())
                            .score(stock.getScores().get(0).getScoreOversea())
                            .diff(stock.getScores().get(0).getDiff())
                            .build();
                    })
                    .collect(Collectors.toList()));
        }
        return Mono.error(new IllegalArgumentException("Invalid country: " + country));
    }

    /**
     * 인기 검색어 api
     */
    public Mono<List<StockHotSearchResponse>> getHotSearch() {
        try {
            // Python 스크립트 경로
            String scriptPath = "hotsearch.py";

            // Python 스크립트를 실행하는 ProcessBuilder
            ProcessBuilder processBuilder = new ProcessBuilder("python3", scriptPath);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Python 스크립트 출력 읽기
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            String output = reader.lines().collect(Collectors.joining("\n"));
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new RuntimeException(
                    "Python script execution failed with exit code: " + exitCode + "\nOutput: "
                        + output);
            }

            // JSON 데이터 파싱
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(output);

            // hot_stocks 리스트 추출
            List<String> hotSymbols = objectMapper.convertValue(
                rootNode.get("hot_stocks"), new TypeReference<List<String>>() {
                });

            // 각 symbol을 stock 테이블에서 조회하며 최대 10개만 추가
            List<StockHotSearchResponse> responses = new ArrayList<>();
            for (String symbol : hotSymbols) {
                Optional<Stock> optionalStock = stockRepository.findBySymbol(symbol);
                if (optionalStock.isPresent()) {
                    Stock stock = optionalStock.get();
                    responses.add(StockHotSearchResponse.builder()
                        .stockId(stock.getId())
                        .symbol(stock.getSymbol())
                        .symbolName(stock.getSymbolName())
                        .country(getCountryFromExchangeNum(stock.getExchangeNum()))
                        .build());
                }
                // 결과 리스트가 10개가 되면 반환
                if (responses.size() >= 10) {
                    break;
                }
            }

            return Mono.just(responses);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute Python script", e);
        }
    }

    /**
     * 종목 요약 api
     */
    /**
     * 종목 요약 데이터를 리턴.
     *
     * @param symbol 종목 심볼
     * @param country 국내/해외 구분
     * @return summarys 리스트
     */
    public Mono<List<String>> getSummarys(String symbol, COUNTRY country) {
        try {
            // Python 스크립트 경로
            String scriptPath = "summary.py";

            // Python 스크립트를 실행하기 위한 ProcessBuilder 설정
            ProcessBuilder processBuilder = new ProcessBuilder("python3", scriptPath, symbol, country.toString());
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Python 스크립트 출력 읽기
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = reader.lines().collect(Collectors.joining("\n"));
            int exitCode = process.waitFor();

            // 프로세스 실행 결과 확인
            if (exitCode != 0) {
                throw new RuntimeException("Python 스크립트 실행 실패 (exit code: " + exitCode + ")\nOutput: " + output);
            }

            // Python 스크립트의 출력 JSON 데이터 파싱
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(output);

            // summarys 리스트 추출
            List<String> summarys = objectMapper.convertValue(
                    rootNode.get("summarys"),
                    new TypeReference<List<String>>() {}
            );

            return Mono.just(summarys);
        } catch (Exception e) {
            throw new RuntimeException("Python 스크립트 실행 중 오류 발생", e);
        }
    }

    /**
     * 종목 차트별 인간지표 api
     * @param category 종목 카테고리
     * @param country 국내/해외 분류
     * @return 종목 차트별 인간지표
     */
    public Mono<List<StockCategoryResponse>> getCategoryStocks(CATEGORY category, COUNTRY country) {
        Mono<List<StockCategoryResponse>> response;

        switch (category) {
            case MARKET:
                response = fetchMarketCapStocks(country);
                break;
            case VOLUME:
                response = fetchVolumeStocks(country);
                break;
            case RISING:
                response = fetchRisingStocks(country);
                break;
            case DESCENT:
                response = fetchDescentStocks(country);
                break;
            default:
                return Mono.error(new IllegalArgumentException("Invalid category"));
        }

        return response;
    }

    private Mono<List<StockCategoryResponse>> fetchMarketCapStocks(COUNTRY country) {
        if (country == COUNTRY.KOREA) {
            return securityService.getMarketCapRankKorea()
                .flatMapIterable(list -> list)
                .flatMap(response -> mapToStockCategoryResponseKorea(
                    response.getMkscShrnIscd(),
                    country,
                    response.getStckPrpr(),
                    response.getPrdyVrss(),
                    response.getPrdyCtrt()))
                .collectList();
        } else {
            return securityService.getMarketCapOversea()
                .flatMapIterable(list -> list)
                .flatMap(response -> mapToStockCategoryResponseOversea(
                    response.getSymb(),
                    country,
                    response.getLast(),
                    response.getDiff(),
                    response.getRate()))
                .collectList();
        }
    }

    private Mono<List<StockCategoryResponse>> fetchVolumeStocks(COUNTRY country) {
        if (country == COUNTRY.KOREA) {
            return securityService.getVolumeRankKoreaForCategory()
                .flatMapIterable(list -> list)
                .flatMap(response -> mapToStockCategoryResponseKorea(
                    response.getMkscShrnIscd(),
                    country,
                    response.getStckPrpr(),
                    response.getPrdyVrss(),
                    response.getPrdyCtrt()))
                .collectList();
        } else {
            return securityService.getVolumeRankOverseaForCategory()
                .flatMapIterable(list -> list)
                .flatMap(response -> mapToStockCategoryResponseOversea(
                    response.getSymb(),
                    country,
                    response.getLast(),
                    response.getDiff(),
                    response.getRate()))
                .collectList();
        }
    }

    private Mono<List<StockCategoryResponse>> fetchRisingStocks(COUNTRY country) {
        if (country == COUNTRY.KOREA) {
            return securityService.getRisingDescentRankKorea(true)
                .flatMapIterable(list -> list)
                .flatMap(response -> mapToStockCategoryResponseKorea(
                    response.getStckShrnIscd(),
                    country,
                    response.getStckPrpr(),
                    response.getPrdyVrss(),
                    response.getPrdyCtrt()))
                .collectList();
        } else {
            return securityService.getRisingDescentRankOversea(true)
                .flatMapIterable(list -> list)
                .flatMap(response -> mapToStockCategoryResponseOversea(
                    response.getSymb(),
                    country,
                    response.getLast(),
                    response.getDiff(),
                    response.getRate()))
                .collectList();
        }
    }

    private Mono<List<StockCategoryResponse>> fetchDescentStocks(COUNTRY country) {
        if (country == COUNTRY.KOREA) {
            return securityService.getRisingDescentRankKorea(false)
                .flatMapIterable(list -> list)
                .flatMap(response -> mapToStockCategoryResponseKorea(
                    response.getStckShrnIscd(),
                    country,
                    response.getStckPrpr(),
                    response.getPrdyVrss(),
                    response.getPrdyCtrt()))
                .collectList();
        } else {
            return securityService.getRisingDescentRankOversea(false)
                .flatMapIterable(list -> list)
                .flatMap(response -> mapToStockCategoryResponseOversea(
                    response.getSymb(),
                    country,
                    response.getLast(),
                    response.getDiff(),
                    response.getRate()))
                .collectList();
        }
    }

    private Mono<StockCategoryResponse> mapToStockCategoryResponseKorea(String symbol,
        COUNTRY country, String price,
        String priceDiff, String priceDiffPercent) {
        return Mono.justOrEmpty(
                stockRepository.findStockBySymbolWithScores(symbol)) // Optional -> Mono 변환
            .flatMap(stock -> {
                // 가격 및 변동률 파싱
                Double parsedPrice = parseDouble(price);
                Double parsedPriceDiff = parseDouble(priceDiff);
                Double parsedPriceDiffPercent = parseDouble(priceDiffPercent);

                // 점수 확인
                List<Score> scores = stock.getScores();
                if (scores.isEmpty()) {
                    return Mono.empty(); // 점수가 없는 경우
                }

                Score latestScore = scores.get(0); // 최신 점수 가져오기
                return Mono.just(StockCategoryResponse.builder()
                    .stockId(stock.getId())
                    .symbolName(stock.getSymbolName())
                    .country(country)
                    .price(parsedPrice)
                    .priceDiff(parsedPriceDiff)
                    .priceDiffPerCent(parsedPriceDiffPercent)
                    .score(latestScore.getScoreKorea())
                    .scoreDiff(latestScore.getDiff())
                    .build());
            });
    }

    private Mono<StockCategoryResponse> mapToStockCategoryResponseOversea(String symbol,
        COUNTRY country, String price,
        String priceDiff, String priceDiffPercent) {
        return Mono.justOrEmpty(stockRepository.findStockBySymbolWithScores(symbol))
            .flatMap(stock -> {
                // 가격 및 변동률 파싱
                Double parsedPrice = parseDouble(price);
                Double parsedPriceDiffPercent = parseDouble(priceDiffPercent);
                // 해외는 diff가 절대값이므로 절대값에 따라 음수로 변경
                Double parsedPriceDiff = parsedPriceDiffPercent < 0 ? parseDouble(priceDiff) * -1
                    : parsedPriceDiffPercent;

                // 점수 확인
                List<Score> scores = stock.getScores();
                if (scores.isEmpty()) {
                    return Mono.empty(); // 점수가 없는 경우
                }

                Score latestScore = scores.get(0); // 최신 점수 가져오기
                return Mono.just(StockCategoryResponse.builder()
                    .stockId(stock.getId())
                    .symbolName(stock.getSymbolName())
                    .country(country)
                    .price(parsedPrice)
                    .priceDiff(parsedPriceDiff)
                    .priceDiffPerCent(parsedPriceDiffPercent)
                    .score(latestScore.getScoreOversea())
                    .scoreDiff(latestScore.getDiff())
                    .build());
            });
    }

    private Double parseDouble(String value) {
        try {
            return value != null ? Double.parseDouble(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 국내/해외 떡상 지표 반환
     * @param country 국내/해외 분류
     * @return 종목 정보
     */
    public List<StockDiffResponse> getRisingStocks(COUNTRY country) {
        List<Score> topScores = getTopScores(country);
        return convertToStockDiffResponses(topScores, country);
    }

    /**
     * 지정된 조건에 따라 상위 9개의 Score 데이터를 조회합니다.
     * 오늘/어제 데이터가 없을 경우 최신 데이터를 사용합니다.
     *
     * @return 상위 9개의 Score 데이터
     */
    private List<Score> getTopScores(COUNTRY country) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        List<Score> allScores;

        if (country == COUNTRY.KOREA) {
            allScores = scoreRepository.findScoresByDatesKorea(today, yesterday);
        } else {
            allScores = scoreRepository.findScoresByDatesOversea(today, yesterday);
        }

        // 오늘/어제 데이터가 없거나 부족한 경우 최신 데이터를 조회
        if (allScores.isEmpty()) {
            if (country == COUNTRY.KOREA) {
                allScores = scoreRepository.findLatestScoresByCountryKorea();
            } else {
                allScores = scoreRepository.findLatestScoresByCountryOversea();
            }
        }

        // 오늘 데이터와 어제 데이터를 구분
        Set<Integer> todayIdSet = allScores.stream()
            .filter(score -> score.getDate().isEqual(today))
            .map(Score::getStockId)
            .collect(Collectors.toSet());

        // TreeSet을 사용해 정렬된 상태 유지
        TreeSet<Score> topScores = new TreeSet<>(Comparator.comparing(Score::getDiff).reversed());

        allScores.stream()
            .filter(score -> {
                if (score.getDate().isEqual(today)) {
                    return true; // 오늘 데이터는 무조건 포함
                }
                // 어제 데이터는 오늘 데이터에 없는 경우에만 포함
                // 최신 데이터도 오늘/어제 데이터에 없는 경우 포함
                return !todayIdSet.contains(score.getStockId());
            })
            .forEach(topScores::add);

        // 상위 9개만 반환
        return topScores.stream()
            .limit(LIMITS)
            .toList();
    }

    /**
     * 지정된 조건에 따라 하위 9개의 Score 데이터를 조회합니다.
     * 오늘/어제 데이터가 없을 경우 최신 데이터를 사용합니다.
     *
     * @return 하위 9개의 Score 데이터
     */
    private List<Score> getBottomScores(COUNTRY country) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        List<Score> allScores;

        if (country == COUNTRY.KOREA) {
            allScores = scoreRepository.findScoresByDatesKorea(today, yesterday);
        } else {
            allScores = scoreRepository.findScoresByDatesOversea(today, yesterday);
        }

        // 오늘/어제 데이터가 없거나 부족한 경우 최신 데이터를 조회
        if (allScores.isEmpty()) {
            if (country == COUNTRY.KOREA) {
                allScores = scoreRepository.findLatestScoresByCountryKorea();
            } else {
                allScores = scoreRepository.findLatestScoresByCountryOversea();
            }
        }

        // 오늘 데이터와 어제 데이터를 구분
        Set<Integer> todayIdSet = allScores.stream()
            .filter(score -> score.getDate().isEqual(today))
            .map(Score::getStockId)
            .collect(Collectors.toSet());

        // TreeSet을 사용해 정렬된 상태 유지
        TreeSet<Score> topScores = new TreeSet<>(Comparator.comparing(Score::getDiff));

        allScores.stream()
            .filter(score -> {
                if (score.getDate().isEqual(today)) {
                    return true; // 오늘 데이터는 무조건 포함
                }
                // 어제 데이터는 오늘 데이터에 없는 경우에만 포함
                // 최신 데이터도 오늘/어제 데이터에 없는 경우 포함
                return !todayIdSet.contains(score.getStockId());
            })
            .forEach(topScores::add);

        // 하위 9개만 반환
        return topScores.stream()
            .limit(LIMITS)
            .toList();
    }

    /**
     * Score 데이터를 StockDiffResponse로 변환합니다.
     *
     * @param scores Score 데이터 목록
     * @return 변환된 StockDiffResponse 목록
     */
    private List<StockDiffResponse> convertToStockDiffResponses(List<Score> scores,
        COUNTRY country) {
        final List<StockDiffResponse> stockDiffResponses = new ArrayList<>();

        // 모든 stockId를 수집하여 배치 조회로 N+1 문제 해결
        List<Integer> stockIds = scores.stream()
            .map(score -> score.getStock().getId())
            .distinct()
            .toList();

        // 배치 조회로 모든 Keyword를 한 번에 가져오기
        List<StockKeyword> stockKeywords = keywordRepository.findKeywordsByStockIds(stockIds);

        // stockId별로 그룹화
        java.util.Map<Integer, List<StockKeyword>> stockKeywordsByStockId = 
            stockKeywords.stream()
                .collect(Collectors.groupingBy(sk -> sk.getStock().getId()));

        // SymbolName을 미리 Map으로 만들어서 효율적으로 접근
        java.util.Map<Integer, String> symbolNameMap = scores.stream()
            .collect(Collectors.toMap(
                score -> score.getStock().getId(),
                score -> score.getStock().getSymbolName(),
                (existing, replacement) -> existing
            ));

        // Score별로 응답 생성
        for (final Score score : scores) {
            Integer stockId = score.getStock().getId();
            String symbolName = symbolNameMap.get(stockId);
            
            // 배치 조회 결과에서 키워드 가져오기 (stockId별로 그룹화된 결과 사용)
            List<String> uniqueKeywords = stockKeywordsByStockId.getOrDefault(stockId, List.of())
                .stream()
                .map(sk -> sk.getKeyword().getName())
                .filter(keyword -> !keyword.equals(symbolName) && isValidKeyword(keyword))
                .distinct()
                .limit(2)
                .toList();

            stockDiffResponses.add(StockDiffResponse.builder()
                .stockId(stockId)
                .symbolName(symbolName)
                .score(country == COUNTRY.KOREA ? score.getScoreKorea() : score.getScoreOversea())
                .diff(score.getDiff())
                .keywords(uniqueKeywords)
                .build());
        }

        return stockDiffResponses;
    }

    private boolean isValidKeyword(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        String specialCharsPattern = "^[a-zA-Z0-9가-힣\\s]+$";
        String postfixPattern = "^(이|가|을|를|의|에|로|으로|에서|와|과|은|는|도|만|까지|부터|마저|조차|나마|처럼|같이|크|등|또|전).*|.*(이|가|을|를|의|에|로|으로|에서|와|과|은|는|도|만|까지|부터|마저|조차|나마|처럼|같이|하|등|또|전)$";

        return name.matches(specialCharsPattern) && !name.matches(postfixPattern);
    }

    /**
     * 국내/해외 떡락 지표 반환
     * @param country 국내/해외 분류
     * @return 종목 정보
     */
    public List<StockDiffResponse> getDescentStocks(COUNTRY country) {
        List<Score> bottomScores = getBottomScores(country);
        return convertToStockDiffResponses(bottomScores, country);
    }

    public List<StockRelevantResponse> getRelevantStocks(final Integer id) {
        Stock searchById = stockRepository.findStockById(id).orElse(null);
        final List<Stock> relevantStocksByExchangeNumAndScore = stockQueryRepository.findRelevantStocksByExchangeNumAndScore(
            searchById);

        if (relevantStocksByExchangeNumAndScore.isEmpty()) {
            System.out.println("Stock " + id + " relevant Stocks are not found");

            return null;
        }

        final List<StockRelevantResponse> stockRelevantResponses = new ArrayList<>();

        for (final Stock stock : relevantStocksByExchangeNumAndScore) {
            final List<String> uniqueKeywords = keywordRepository.findKeywordsByStockId(
                    stock.getId(), PageRequest.of(0, 10))
                .stream()
                .map(Keyword::getName)
                .filter(keyword -> (!keyword.equals(stock.getSymbolName()) && isValidKeyword(
                    keyword))) // symbolName과 일치하는 키워드 제거
                .distinct()
                .limit(2)
                .toList();

            stockRelevantResponses.add(StockRelevantResponse.builder()
                .stockId(stock.getId())
                .symbolName(stock.getSymbolName())
                .keywords(uniqueKeywords)
                .score(stock.getExchangeNum()
                    .equals(EXCHANGENUM.KOSPI) || stock.getExchangeNum()
                    .equals(EXCHANGENUM.KOSDAQ) || stock.getExchangeNum()
                    .equals(EXCHANGENUM.KOREAN_ETF) ? stock.getScores().get(0).getScoreKorea()
                    : stock.getScores().get(0).getScoreOversea())
                .diff(stock.getScores().get(0).getDiff())
                .build());
        }

        return stockRelevantResponses;
    }

    public Mono<StockInfoResponse> getStockInfo(Integer id, COUNTRY country) {
        // TODO: 비 블로킹 컨텍스트 관련 처리
        Stock stock = stockRepository.findStockById(id)
            .orElseThrow(() -> new RuntimeException("no stock found"));
        return securityService.getSecurityStockInfoKorea(stock.getId(), stock.getSymbolName(),
            stock.getSecurityName(), stock.getSymbol(), stock.getExchangeNum(),
            getCountryFromExchangeNum(stock.getExchangeNum()));
    }

    public StockDetailResponse getStockDetailInfo(Integer id, COUNTRY country) {
        // TODO: 비 블로킹 컨텍스트 관련 처리
        Stock stock = stockRepository.findStockById(id)
                .orElseThrow(() -> new RuntimeException("no stock found"));

        List<String> keywords = keywordRepository.findKeywordsByStockId(id, PageRequest.of(0, 2))
                .stream()
                .map(Keyword::getName)
                .filter(this::isValidKeyword)
                .distinct()
                .toList();

        StockInfoResponse stockInfoKorea = securityService.getSecurityStockInfoKorea(stock.getId(), stock.getSymbolName(),
                stock.getSecurityName(), stock.getSymbol(), stock.getExchangeNum(),
                getCountryFromExchangeNum(stock.getExchangeNum())).block();

        return StockDetailResponse.builder()
                .stockId(stockInfoKorea.getStockId())
                .symbolName(stockInfoKorea.getSymbolName())
                .securityName(stockInfoKorea.getSecurityName())
                .symbol(stockInfoKorea.getSymbol())
                .exchangeNum(stockInfoKorea.getExchangeNum())
                .country(stockInfoKorea.getCountry())
                .price(stockInfoKorea.getPrice())
                .priceDiff(stockInfoKorea.getPriceDiff())
                .priceDiffPerCent(stockInfoKorea.getPriceDiffPerCent())
                .score(country == COUNTRY.KOREA ? stock.getScores().get(0).getScoreKorea() : stock.getScores().get(0).getScoreOversea())
                .scoreDiff(stock.getScores().get(0).getDiff())
                .keywords(keywords)
                .build();
    }

    private COUNTRY getCountryFromExchangeNum(EXCHANGENUM exchangenum) {
        return List.of(EXCHANGENUM.KOSPI, EXCHANGENUM.KOSDAQ, EXCHANGENUM.KOREAN_ETF)
            .contains(exchangenum) ? COUNTRY.KOREA : COUNTRY.OVERSEA;
    }

    public Mono<StockChartResponse> getStockChart(final Integer id, String periodCode, LocalDate startDate, LocalDate endDate) {
        final Stock stock = stockRepository.findStockById(id).orElse(null);

        if (stock == null) {
            System.out.println("no stock found (stockId: " + id + ")");

            return null;
        }

        String startDateToString, endDateToString;

        if (endDate == null) {
            endDate = LocalDate.now();
        }

        endDateToString = endDate.format(DateTimeFormatter.BASIC_ISO_DATE);

        if (startDate == null) {
            startDate = getStartDate(periodCode, endDate);
        }

        startDateToString = startDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        final COUNTRY country = getCountry(stock);

        final List<PriceInfo> itemChartPrices = securityService.getItemChartPrice(stock, startDateToString, endDateToString, periodCode, country).block();

        if (itemChartPrices == null) {
            System.out.println("no found itemCharPrice (stockId: " + stock.getId() + ")");

            return null;
        }

        List<Score> scores = stock.getScores();
        List<StockChartResponse.PriceInfo> priceInfos = new ArrayList<>();

        for (PriceInfo priceInfo : itemChartPrices) {
            LocalDate priceDate = LocalDate.parse(priceInfo.getLocalDate(), DateTimeFormatter.BASIC_ISO_DATE);
            Score matchingScore = null;

            // 날짜에 맞는 Score 찾기
            for (Score score : scores) {
                if (score.getDate().equals(priceDate)) {
                    matchingScore = score;
                    break;
                }
            }

            // PriceInfo와 Score 병합
            StockChartResponse.PriceInfo enrichedPriceInfo = StockChartResponse.PriceInfo.builder()
                .localDate(priceInfo.getLocalDate())
                .closePrice(priceInfo.getClosePrice())
                .openPrice(priceInfo.getOpenPrice())
                .highPrice(priceInfo.getHighPrice())
                .lowPrice(priceInfo.getLowPrice())
                .accumulatedTradingVolume(priceInfo.getAccumulatedTradingVolume())
                .accumulatedTradingValue(priceInfo.getAccumulatedTradingValue())
                .score(matchingScore != null ? (country == COUNTRY.KOREA ? matchingScore.getScoreKorea() : matchingScore.getScoreOversea()) : null)
                .diff(matchingScore != null ? matchingScore.getDiff() : null)
                .build();

            priceInfos.add(enrichedPriceInfo);
        }

        // StockChartResponse 생성
        StockChartResponse response = StockChartResponse.builder()
            .symbolName(stock.getSymbolName())
            .symbol(stock.getSymbol())
            .exchangenum(stock.getExchangeNum())
            .securityName(stock.getSecurityName())
            .country(country)
            .priceInfos(priceInfos)
            .build();

        return Mono.just(response);
    }

    private static LocalDate getStartDate(String periodCode, LocalDate endDate) {
        // 시작일자와 종료일자가 없는 경우, 기간분류코드를 기반으로 계산
        return switch (periodCode) {
            case "D" -> // 일봉: 최근 30일
                endDate.minusDays(30);
            case "W" -> // 주봉: 최근 6개월
                endDate.minusMonths(6);
            case "M" -> // 월봉: 최근 2년
                endDate.minusYears(2);
            default -> throw new IllegalArgumentException("Invalid period code: " + periodCode);
        };
    }

    private static COUNTRY getCountry(Stock stock) {
        COUNTRY country;
        if (stock.getExchangeNum() == EXCHANGENUM.KOSPI
            || stock.getExchangeNum() == EXCHANGENUM.KOSDAQ
            || stock.getExchangeNum() == EXCHANGENUM.KOREAN_ETF) {
            country = COUNTRY.KOREA;
        } else {
            country = COUNTRY.OVERSEA;
        }

        return country;
    }

    public Mono<StockChartResponse> convertToStockChartResponse(
        Mono<List<PriceInfo>> itemChartPrice, Stock stock, COUNTRY country) {
        final List<Score> scores = stock.getScores();
        // priceinfos 의 각 날짜에 해당하는 score를 반환.

        return itemChartPrice.map(
            priceInfos -> StockChartResponse.builder()
                .symbolName(stock.getSymbolName())
                .symbol(stock.getSymbol())
                .exchangenum(stock.getExchangeNum())
                .securityName(stock.getSecurityName())
                .country(List.of(EXCHANGENUM.KOSPI, EXCHANGENUM.KOSDAQ, EXCHANGENUM.KOREAN_ETF)
                    .contains(stock.getExchangeNum()) ? COUNTRY.KOREA : COUNTRY.OVERSEA)
                .priceInfos(priceInfos)
                .build()
        );
    }

    /**
     * 특정 DomesticSector의 주식을 추천합니다.
     * 점수 기반 가중치 랜덤 추천을 사용합니다.
     * 
     * 금융 서비스이므로 UNKNOWN 섹터는 추천 대상에서 제외합니다.
     * 
     * @param sector 추천할 국내 섹터
     * @return 추천된 주식(Stock) 엔티티, 없으면 null
     */
    public Stock getRecommendedStockByDomesticSector(DomesticSector sector) {
        // UNKNOWN 섹터는 추천하지 않음 (부정확한 정보 제공 방지)
        if (sector == null || sector == DomesticSector.UNKNOWN) {
            return null;
        }
        
        LocalDate today = LocalDate.now();
        
        // 해당 DomesticSector의 valid=true인 주식만 조회
        List<Stock> validStocks = stockRepository.findValidStocksByDomesticSector(sector);
        
        if (validStocks.isEmpty()) {
            return null;
        }
        
        // 배치로 점수 조회 (N+1 문제 해결)
        List<Integer> candidateStockIds = validStocks.stream()
                .map(Stock::getId)
                .collect(Collectors.toList());
        
        // 오늘 날짜 점수와 최신 점수를 배치로 조회
        List<Score> todayScores = scoreRepository.findTodayScoresByStockIds(candidateStockIds, today);
        List<Score> latestScores = scoreRepository.findLatestScoresByStockIds(candidateStockIds);
        
        // stockId -> Score 맵 생성 (오늘 점수 우선, 없으면 최신 점수)
        Map<Integer, Score> scoreMap = new HashMap<>();
        todayScores.forEach(score -> scoreMap.put(score.getStockId(), score));
        latestScores.forEach(score -> scoreMap.putIfAbsent(score.getStockId(), score));
        
        // 점수가 있는 주식만 필터링
        List<Stock> stocksWithScore = validStocks.stream()
                .filter(stock -> scoreMap.containsKey(stock.getId()))
                .collect(Collectors.toList());
        
        if (stocksWithScore.isEmpty()) {
            return null;
        }
        
        // 각 주식의 가중치 계산 (점수 맵을 전달하여 메모리에서 조회)
        List<StockWithWeight> stocksWithWeight = calculateWeightsForSector(stocksWithScore, scoreMap);
        
        // 가중치 기반 랜덤 선택
        Random random = new Random(System.currentTimeMillis());
        return selectWeightedRandom(stocksWithWeight, random);
    }

    /**
     * 특정 OverseasSector의 주식을 추천합니다.
     * 점수 기반 가중치 랜덤 추천을 사용합니다.
     * 
     * 금융 서비스이므로 UNKNOWN 섹터는 추천 대상에서 제외합니다.
     * 
     * @param sector 추천할 해외 섹터
     * @return 추천된 주식(Stock) 엔티티, 없으면 null
     */
    public Stock getRecommendedStockByOverseasSector(OverseasSector sector) {
        // UNKNOWN 섹터는 추천하지 않음 (부정확한 정보 제공 방지)
        if (sector == null || sector == OverseasSector.UNKNOWN) {
            return null;
        }
        
        LocalDate today = LocalDate.now();
        
        // 해당 OverseasSector의 valid=true인 주식만 조회
        List<Stock> validStocks = stockRepository.findValidStocksByOverseasSector(sector);
        
        if (validStocks.isEmpty()) {
            return null;
        }
        
        // 배치로 점수 조회 (N+1 문제 해결)
        List<Integer> candidateStockIds = validStocks.stream()
                .map(Stock::getId)
                .collect(Collectors.toList());
        
        // 오늘 날짜 점수와 최신 점수를 배치로 조회
        List<Score> todayScores = scoreRepository.findTodayScoresByStockIds(candidateStockIds, today);
        List<Score> latestScores = scoreRepository.findLatestScoresByStockIds(candidateStockIds);
        
        // stockId -> Score 맵 생성 (오늘 점수 우선, 없으면 최신 점수)
        Map<Integer, Score> scoreMap = new HashMap<>();
        todayScores.forEach(score -> scoreMap.put(score.getStockId(), score));
        latestScores.forEach(score -> scoreMap.putIfAbsent(score.getStockId(), score));
        
        // 점수가 있는 주식만 필터링
        List<Stock> stocksWithScore = validStocks.stream()
                .filter(stock -> scoreMap.containsKey(stock.getId()))
                .collect(Collectors.toList());
        
        if (stocksWithScore.isEmpty()) {
            return null;
        }
        
        // 각 주식의 가중치 계산 (점수 맵을 전달하여 메모리에서 조회)
        List<StockWithWeight> stocksWithWeight = calculateWeightsForSector(stocksWithScore, scoreMap);
        
        // 가중치 기반 랜덤 선택
        Random random = new Random(System.currentTimeMillis());
        return selectWeightedRandom(stocksWithWeight, random);
    }

    /**
     * 각 주식의 가중치를 계산합니다.
     * 점수 기반 가중치를 사용합니다.
     */
    private List<StockWithWeight> calculateWeightsForSector(List<Stock> stocks, Map<Integer, Score> scoreMap) {
        return stocks.stream()
                .map(stock -> {
                    Score score = scoreMap.get(stock.getId());
                    if (score == null) {
                        throw new IllegalStateException("주식(id:" + stock.getId() + ")에 점수가 없습니다.");
                    }
                    int scoreValue = getScoreByCountry(score, stock.getExchangeNum());
                    double scoreWeight = calculateScoreWeight(scoreValue);
                    
                    return new StockWithWeight(stock, scoreWeight);
                })
                .collect(Collectors.toList());
    }

    /**
     * 국가별 점수를 반환합니다.
     */
    private int getScoreByCountry(Score score, EXCHANGENUM exchangeNum) {
        boolean isKorea = List.of(EXCHANGENUM.KOSPI, EXCHANGENUM.KOSDAQ, EXCHANGENUM.KOREAN_ETF)
                .contains(exchangeNum);
        return isKorea ? score.getScoreKorea() : score.getScoreOversea();
    }

    /**
     * 점수 기반 가중치를 계산합니다.
     * 점수가 높을수록 선택 확률이 증가하지만, 너무 극단적이지 않도록 부드러운 곡선 적용.
     * 0점: 1.0, 50점: 6.0, 100점: 11.0 (제곱근 곡선 사용)
     */
    private double calculateScoreWeight(int score) {
        // 점수를 0-100 범위로 제한
        score = Math.max(0, Math.min(100, score));
        
        // 제곱근 곡선: sqrt(score/100) * 10 + 1
        // 0점 -> 1.0, 50점 -> 8.07, 100점 -> 11.0
        return Math.sqrt(score / 100.0) * 10.0 + 1.0;
    }

    /**
     * 가중치 기반 랜덤 선택을 수행합니다.
     */
    private Stock selectWeightedRandom(List<StockWithWeight> stocksWithWeight, Random random) {
        if (stocksWithWeight.isEmpty()) {
            throw new IllegalStateException("추천할 주식이 없습니다.");
        }
        
        // 총 가중치 계산
        double totalWeight = stocksWithWeight.stream()
                .mapToDouble(sw -> sw.weight)
                .sum();
        
        // 랜덤 값 생성 (0 ~ totalWeight)
        double randomValue = random.nextDouble() * totalWeight;
        
        // 누적 가중치를 따라 선택
        double cumulativeWeight = 0.0;
        for (StockWithWeight sw : stocksWithWeight) {
            cumulativeWeight += sw.weight;
            if (randomValue <= cumulativeWeight) {
                return sw.stock;
            }
        }
        
        // 마지막 주식 반환 (반올림 오차 대비)
        return stocksWithWeight.get(stocksWithWeight.size() - 1).stock;
    }

    /**
     * 주식과 가중치를 함께 담는 내부 클래스
     */
    private static class StockWithWeight {
        final Stock stock;
        final double weight;

        StockWithWeight(Stock stock, double weight) {
            this.stock = stock;
            this.weight = weight;
        }
    }

}

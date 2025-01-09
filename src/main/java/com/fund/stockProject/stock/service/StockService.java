package com.fund.stockProject.stock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fund.stockProject.global.config.SecurityHttpConfig;
import com.fund.stockProject.keyword.entity.Keyword;
import com.fund.stockProject.keyword.repository.KeywordRepository;
import com.fund.stockProject.score.entity.Score;
import com.fund.stockProject.score.repository.ScoreRepository;
import com.fund.stockProject.score.service.ScoreService;
import com.fund.stockProject.stock.domain.CATEGORY;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.domain.EXCHANGENUM;
import com.fund.stockProject.stock.dto.response.StockCategoryResponse;
import com.fund.stockProject.stock.dto.response.StockChartResponse;
import com.fund.stockProject.stock.dto.response.StockChartResponse.PriceInfo;
import com.fund.stockProject.stock.dto.response.StockDiffResponse;
import com.fund.stockProject.stock.dto.response.StockInfoResponse;
import com.fund.stockProject.stock.dto.response.StockRelevantResponse;
import com.fund.stockProject.stock.dto.response.StockSearchResponse;
import com.fund.stockProject.stock.dto.response.StockSimpleResponse;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.repository.StockQueryRepository;
import com.fund.stockProject.stock.repository.StockRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

    public Mono<StockInfoResponse> searchStockBySymbolName(final String searchKeyword, final String country) {
        List<EXCHANGENUM> koreaExchanges = List.of(EXCHANGENUM.KOSPI, EXCHANGENUM.KOSDAQ, EXCHANGENUM.KOREAN_ETF);
        List<EXCHANGENUM> overseaExchanges = List.of(EXCHANGENUM.NAS, EXCHANGENUM.NYS, EXCHANGENUM.AMS);

        final Optional<Stock> bySymbolNameAndCountryWithEnums = stockRepository.findBySymbolNameAndCountryWithEnums(
            searchKeyword, country, koreaExchanges, overseaExchanges);

        if(bySymbolNameAndCountryWithEnums.isPresent()){
            final Stock stock = bySymbolNameAndCountryWithEnums.get();

            return securityService.getSecurityStockInfoKorea(stock.getId(), stock.getSymbolName(),
                stock.getSecurityName(), stock.getSymbol(), stock.getExchangeNum(), getCountryFromExchangeNum(stock.getExchangeNum()));
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
                            .build();
                    })
                    .collect(Collectors.toList()));
        }
        return Mono.error(new IllegalArgumentException("Invalid country: " + country));
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
                Double parsedPriceDiff = parsedPriceDiffPercent < 0 ? parseDouble(priceDiff) * -1 : parsedPriceDiffPercent;

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
     *
     * @return 상위 3개의 Score 데이터
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
     *
     * @return 하위 3개의 Score 데이터
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
                return !todayIdSet.contains(score.getStockId());
            })
            .forEach(topScores::add);

        // 상위 9개만 반환
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

        for (final Score score : scores) {
            final List<String> uniqueKeywords = keywordRepository.findKeywordsByStockId(
                    score.getStock().getId(), PageRequest.of(0, 10))
                .stream()
                .map(Keyword::getName)
                .filter(
                    keyword -> (!keyword.equals(score.getStock().getSymbolName()) && isValidKeyword(
                        keyword))) // symbolName과 일치하는 키워드 제거
                .distinct()
                .limit(2)
                .toList();

            stockDiffResponses.add(StockDiffResponse.builder()
                .stockId(score.getStock().getId())
                .symbolName(score.getStock().getSymbolName())
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

    private COUNTRY getCountryFromExchangeNum(EXCHANGENUM exchangenum) {
        return List.of(EXCHANGENUM.KOSPI, EXCHANGENUM.KOSDAQ, EXCHANGENUM.KOREAN_ETF)
            .contains(exchangenum) ? COUNTRY.KOREA : COUNTRY.OVERSEA;
    }

    public Mono<StockChartResponse> getStockChart(final Integer id, String periodCode,
        LocalDate startDate) {
        final Stock stock = stockRepository.findStockById(id).orElse(null);

        if (stock == null) {
            System.out.println("Stock " + id + " can not found");

            return null;
        }

        final LocalDate endDate = LocalDate.now();
        final String endDateToString = endDate.format(DateTimeFormatter.BASIC_ISO_DATE);

        if (startDate == null) {
            startDate = getStartDate(periodCode, endDate);
        }

        final String startDateToString = startDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        final COUNTRY country = getCountry(stock);

        final List<PriceInfo> itemChartPrices = securityService.getItemChartPrice(stock,
            startDateToString, endDateToString, periodCode, country).block();

        List<Score> scores = stock.getScores();
        List<StockChartResponse.PriceInfo> priceInfos = new ArrayList<>();

        if (itemChartPrices == null) {
            System.out.println("There is no itemCharPrice data");

            return null;
        }

        for (PriceInfo priceInfo : itemChartPrices) {
            LocalDate priceDate = LocalDate.parse(priceInfo.getLocalDate(),
                DateTimeFormatter.BASIC_ISO_DATE);
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
                .score(matchingScore != null ? (country == COUNTRY.KOREA
                    ? matchingScore.getScoreKorea() : matchingScore.getScoreOversea()) : null)
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

}

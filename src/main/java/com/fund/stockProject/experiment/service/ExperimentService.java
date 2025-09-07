package com.fund.stockProject.experiment.service;

import com.fund.stockProject.auth.entity.User;
import com.fund.stockProject.auth.repository.UserRepository;
import com.fund.stockProject.experiment.dto.ExperimentSimpleResponse;
import com.fund.stockProject.experiment.dto.ExperimentStatusResponse;
import com.fund.stockProject.experiment.dto.ExperimentItemInfoResponse;
import com.fund.stockProject.experiment.entity.ExperimentItem;
import com.fund.stockProject.experiment.repository.ExperimentRepository;
import com.fund.stockProject.score.repository.ScoreRepository;
import com.fund.stockProject.security.principle.CustomUserDetails;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.domain.EXCHANGENUM;
import com.fund.stockProject.stock.dto.response.StockInfoResponse;
import com.fund.stockProject.stock.dto.response.StockSearchResponse;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.repository.StockQueryRepository;
import com.fund.stockProject.stock.repository.StockRepository;
import com.fund.stockProject.stock.service.SecurityService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ExperimentService {

    private final ExperimentRepository experimentRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final ScoreRepository scoreRepository;
    private final SecurityService securityService;
    private final StockQueryRepository stockQueryRepository;

    public Mono<ExperimentStatusResponse> getExperimentStatus(
        final CustomUserDetails customUserDetails) {
        // 로그인한 유저 관련 모의 투자 정보 조회
        final List<ExperimentItem> experimentItemsByUserId = experimentRepository.findExperimentItemsByEmail(
            customUserDetails.getEmail());

        if (experimentItemsByUserId.isEmpty()) {
            return Mono.empty();
        }

        // 진행중인 모의 투자 종목
        final List<ExperimentItemInfoResponse> progressExperimentItemsInfo = new ArrayList<>();
        // 완료된 모의 투자 종목
        final List<ExperimentItemInfoResponse> completeExperimentItemsInfo = new ArrayList<>();

        for (final ExperimentItem experimentItem : experimentItemsByUserId) {
            final Optional<Stock> bySymbol = stockRepository.findBySymbol(
                experimentItem.getStock().getSymbol());

            if (bySymbol.isEmpty()) {
                throw new NoSuchElementException("No Stock Found");
            }

            final Stock stock = bySymbol.get();

            StockInfoResponse stockInfoKorea = securityService.getSecurityStockInfoKorea(
                stock.getId(),
                stock.getSymbolName(),
                stock.getSecurityName(),
                stock.getSymbol(),
                stock.getExchangeNum(),
                getCountryFromExchangeNum(stock.getExchangeNum())
            ).block();

            if (experimentItem.getTradeStatus().equals("PROGRESS")) {
                progressExperimentItemsInfo.add(ExperimentItemInfoResponse
                    .builder()
                    .id(experimentItem.getId())
                    .roi(experimentItem.getRoi())
                    .buyAt(experimentItem.getBuyAt())
                    .symbolName(stock.getSymbolName())
                    .currentPrice(stockInfoKorea.getPrice())
                    .diffPrice((stockInfoKorea.getPrice()) - experimentItem.getBuyPrice())
                    .tradeStatus(experimentItem.getTradeStatus())
                    .build());

                continue;
            }

            completeExperimentItemsInfo.add(ExperimentItemInfoResponse
                .builder()
                .id(experimentItem.getId())
                .roi(experimentItem.getRoi())
                .buyAt(experimentItem.getBuyAt())
                .symbolName(stock.getSymbolName())
                .currentPrice(stockInfoKorea.getPrice())
                .diffPrice((stockInfoKorea.getPrice()) - experimentItem.getBuyPrice())
                .tradeStatus(experimentItem.getTradeStatus())
                .build());
        }

        final int countByTradeStatusCompleted = experimentRepository.countByTradeStatusProgress(); // 진행중인 실험 수

        final double averageRoi = experimentItemsByUserId
            .stream()
            .mapToDouble(ExperimentItem::getRoi) // 각 ROI 값을 double로 추출
            .average()                            // OptionalDouble 반환
            .orElse(0.0);

        final long count = experimentItemsByUserId.stream()
            .filter(p -> p.getSellPrice() - p.getBuyPrice() > 0).count(); // 모의투자에 성공한 종목 개수
        double successRate = ((double) count / experimentItemsByUserId.size()) * 100;

        final ExperimentStatusResponse experimentStatusResponse = ExperimentStatusResponse.
            builder()
            .progressExperimentItems(progressExperimentItemsInfo) // 진행중인 실험 정보
            .completeExperimentItems(completeExperimentItemsInfo) // 완료된 실험 정보
            .avgRoi(averageRoi) // 평균 수익률
            .totalPaperTradeCount(experimentItemsByUserId.size()) // 총 실험 수 (전체 모의투자 개수)
            .progressPaperTradeCount(countByTradeStatusCompleted) // 진행중인 실험 수 (진행중인 모의투자 개수)
            .successRate(successRate) // 성공률
            .build();

        return Mono.just(experimentStatusResponse);
    }

    private COUNTRY getCountryFromExchangeNum(EXCHANGENUM exchangenum) {
        return List.of(EXCHANGENUM.KOSPI, EXCHANGENUM.KOSDAQ, EXCHANGENUM.KOREAN_ETF)
            .contains(exchangenum) ? COUNTRY.KOREA : COUNTRY.OVERSEA;
    }

    public Mono<ExperimentSimpleResponse> buyExperimentItem(final CustomUserDetails customUserDetails, final Integer stockId, String country) {
        final Optional<Stock> stockById = stockRepository.findStockById(stockId);
        final Optional<User> userById = userRepository.findByEmail(customUserDetails.getEmail());

        if (stockById.isEmpty() || userById.isEmpty()) {
            return Mono.empty();
        }

        final Stock stock = stockById.get();
        final User user = userById.get();

        LocalDateTime now = LocalDateTime.now();
        LocalTime current = now.toLocalTime();
        LocalDate today = now.toLocalDate();
        LocalTime koreaUpdateTime = LocalTime.of(17, 0);
        LocalTime overseasUpdateTime = LocalTime.of(6, 0);
        Double price = 0.0d;

        final Mono<StockInfoResponse> securityStockInfoKorea = securityService.getSecurityStockInfoKorea(stock.getId(), stock.getSymbolName(),
            stock.getSecurityName(), stock.getSymbol(), stock.getExchangeNum(), getCountryFromExchangeNum(stock.getExchangeNum()));

        if (securityStockInfoKorea.blockOptional().isEmpty()) {
            return Mono.empty();
        }

        final StockInfoResponse stockInfoResponse = securityStockInfoKorea.block();

        if (stockInfoResponse.getCountry().equals(COUNTRY.KOREA)) {
            final Optional<ExperimentItem> experimentItemByStockIdAndBuyAt = experimentRepository.findExperimentItemByStockIdAndBuyAt(stockId, today);

            // 하루에 같은 종목 중복 구매 불가 처리
            if (experimentItemByStockIdAndBuyAt.isPresent()) {
                return Mono.just(
                    ExperimentSimpleResponse.builder()
                        .message("같은 종목 중복 구매")
                        .success(false)
                        .price(0.0d)
                        .build()
                );
            }

            // 종가 결정
            price = current.isBefore(koreaUpdateTime) ? stockInfoResponse.getYesterdayPrice() : stockInfoResponse.getTodayPrice();
        } else {
            // 해외 주식 로직
            LocalDateTime overseasStartTime = now.withHour(6).withMinute(0).withSecond(0).withNano(0); // 오늘 06:00
            LocalDateTime overseasPreviousDayTime = overseasStartTime.minusDays(1).plusMinutes(1); // 전날 06:01

            // 해당 구간에 이미 매수한 경우 중복 매수 방지
            Optional<ExperimentItem> existingItem = experimentRepository.findExperimentItemByStockIdAndBuyAtBetween(stockId, overseasPreviousDayTime, overseasStartTime);

            if (existingItem.isPresent()) {
                return Mono.just(ExperimentSimpleResponse.builder()
                    .message("같은 종목 중복 구매")
                    .success(false)
                    .price(0.0d)
                    .build()
                );
            }

            // 종가 결정
            price = current.isBefore(overseasUpdateTime) ? stockInfoResponse.getYesterdayPrice() : stockInfoResponse.getTodayPrice();
        }

        final ExperimentItem experimentItem = ExperimentItem.builder()
            .user(user)
            .stock(stock)
            .tradeStatus("PROGRESS")
            .buyAt(now)
            .buyPrice(price)
            .build();

        experimentRepository.save(experimentItem);

        return Mono.just(ExperimentSimpleResponse.builder()
                .message("모의 매수 성공")
                .success(true)
                .price(price)
                .build()
        );
    }

    @Transactional(readOnly = true)
    public List<ExperimentItem> findExperimentItemAfter5BusinessDays() {
        final List<ExperimentItem> experimentItemsAfter5BusinessDays = experimentRepository.findExperimentItemsAfter5BusinessDays();

        if (experimentItemsAfter5BusinessDays.isEmpty()) {
            return new ArrayList<>();
        }

        return experimentItemsAfter5BusinessDays;
    }

    @Transactional
    public void updateAutoSellStockStatus(ExperimentItem experimentItem) {
        try {
            final Stock stock = experimentItem.getStock();

            final Mono<StockInfoResponse> securityStockInfoKorea = securityService.getSecurityStockInfoKorea(
                stock.getId(), stock.getSymbolName(), stock.getSecurityName(), stock.getSymbol(),
                stock.getExchangeNum(), getCountryFromExchangeNum(stock.getExchangeNum()));

            if (securityStockInfoKorea.blockOptional().isPresent()) {
                final Double price = securityStockInfoKorea.block().getTodayPrice();
                Double roi =
                    ((experimentItem.getBuyPrice() - price) % experimentItem.getBuyPrice()) * 100;
                experimentItem.updateAutoSellResult(price, "COMPLETE", LocalDateTime.now(), roi);
            }

        } catch (Exception e) {
            System.err.println("Failed to autoSell");
        }
    }

    public Mono<StockInfoResponse> searchStockBySymbolName(final String searchKeyword, final String country) {
        List<EXCHANGENUM> koreaExchanges = List.of(EXCHANGENUM.KOSPI, EXCHANGENUM.KOSDAQ, EXCHANGENUM.KOREAN_ETF);
        List<EXCHANGENUM> overseaExchanges = List.of(EXCHANGENUM.NAS, EXCHANGENUM.NYS, EXCHANGENUM.AMS);

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
}

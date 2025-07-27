package com.fund.stockProject.experiment.service;

import com.fund.stockProject.auth.entity.User;
import com.fund.stockProject.auth.repository.UserRepository;
import com.fund.stockProject.experiment.dto.ExperimentSimpleResponse;
import com.fund.stockProject.experiment.dto.ExperimentStatusResponse;
import com.fund.stockProject.experiment.dto.ProgressExperimentItemResponse;
import com.fund.stockProject.experiment.entity.ExperimentItem;
import com.fund.stockProject.experiment.repository.ExperimentRepository;
import com.fund.stockProject.score.repository.ScoreRepository;
import com.fund.stockProject.security.principle.CustomUserDetails;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.domain.EXCHANGENUM;
import com.fund.stockProject.stock.dto.response.StockInfoResponse;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.repository.StockRepository;
import com.fund.stockProject.stock.service.SecurityService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ExperimentService {

    private final ExperimentRepository experimentRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final ScoreRepository scoreRepository;
    private final SecurityService securityService;

    public Mono<ExperimentStatusResponse> getExperimentStatus(final CustomUserDetails customUserDetails) {
        final List<ExperimentItem> experimentItemsByUserId = experimentRepository.findExperimentItemsByEmail(customUserDetails.getEmail());

        if (experimentItemsByUserId.isEmpty()) {
            return Mono.empty();
        }

        final List<ProgressExperimentItemResponse> progressExperimentItems = new ArrayList<>();

        for (final ExperimentItem experimentItem : experimentItemsByUserId) {
            final Optional<Stock> bySymbol = stockRepository.findBySymbol(experimentItem.getStock().getSymbol());

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

            progressExperimentItems.add(ProgressExperimentItemResponse
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

        final int countByTradeStatusCompleted = experimentRepository.countByTradeStatusProgress(); // 진행중인 실험

        final double averageRoi = experimentItemsByUserId
            .stream()
            .mapToDouble(ExperimentItem::getRoi) // 각 ROI 값을 double로 추출
            .average()                            // OptionalDouble 반환
            .orElse(0.0);

        final long count = experimentItemsByUserId.stream()
            .filter(p -> p.getSellPrice() - p.getBuyPrice() > 0).count(); // 모의투자에 성공한 종목 개수
        double successRate = ((double) count / experimentItemsByUserId.size()) * 100;

        final ExperimentStatusResponse portfolioStatusResponse = ExperimentStatusResponse.
            builder()
            .progressExperimentItems(progressExperimentItems) // 진행중인 실험 정보
            .avgRoi(averageRoi) // 평균 수익률
            .totalPaperTradeCount(experimentItemsByUserId.size()) // 총 실험 수 (전체 모의투자 개수)
            .progressPaperTradeCount(countByTradeStatusCompleted) // 진행중인 실험 수 (진행중인 모의투자 개수)
            .successRate(successRate) // 성공률
            .build();

        return Mono.just(portfolioStatusResponse);
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
        LocalDate today = now.toLocalDate();
        LocalTime current = now.toLocalTime();

        final Optional<ExperimentItem> experimentItemByStockIdAndBuyAt = experimentRepository.findExperimentItemByStockIdAndBuyAt(stockId, today);

        if(experimentItemByStockIdAndBuyAt.isPresent()){
            return Mono.just(
                ExperimentSimpleResponse.builder()
                    .message("같은 종목 중복 구매")
                    .success(false)
                    .price(0.0d)
                    .build()
            );
        }

        LocalTime domesticUpdateTime = LocalTime.of(17, 00);
        LocalTime overseasUpdateTime = LocalTime.of(6, 0);

        Double price = 0.0d;

        final Mono<StockInfoResponse> securityStockInfoKorea = securityService.getSecurityStockInfoKorea(
            stock.getId(), stock.getSymbolName(), stock.getSecurityName(), stock.getSymbol(), stock.getExchangeNum(), getCountryFromExchangeNum(stock.getExchangeNum())
        );

        if(securityStockInfoKorea.blockOptional().isEmpty()){
            return Mono.empty();
        }

        final StockInfoResponse stockInfoResponse = securityStockInfoKorea.block();

        if (stockInfoResponse.getCountry().equals(COUNTRY.KOREA)) {
            if (current.isBefore(domesticUpdateTime)) {
                // 17:00 이전 → 전일 종가
                price = stockInfoResponse.getYesterdayPrice();
            } else {
                // 17:00 이후 → 당일 종가
                price = stockInfoResponse.getPrice();
            }
        } else {
            if (current.isBefore(overseasUpdateTime)) {
                // 06:00 이전 → 전일(미국시장 종가) → KST 기준 ‘전날’
                price = stockInfoResponse.getYesterdayPrice();
            } else {
                // 06:00 이후 → 당일(미국시장 전날 종가) → KST 기준 ‘오늘’
                price = stockInfoResponse.getTodayPrice();
            }
        }

        final ExperimentItem experimentItem = ExperimentItem
            .builder()
            .user(user)
            .stock(stock)
            .tradeStatus("PROGRESS")
            .buyAt(LocalDateTime.now())
            .buyPrice(price)
            .build();

        experimentRepository.save(experimentItem);

        return Mono.just(
            ExperimentSimpleResponse.builder()
                .message("모의 매수 성공")
                .success(true)
                .price(price)
                .build()
        );
    }
}

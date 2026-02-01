package com.fund.stockProject.experiment.service;

import com.fund.stockProject.user.entity.User;
import com.fund.stockProject.user.repository.UserRepository;
import com.fund.stockProject.experiment.domain.SCORERANGE;
import com.fund.stockProject.experiment.dto.ExperimentReportResponse;
import com.fund.stockProject.experiment.dto.ExperimentSimpleResponse;
import com.fund.stockProject.experiment.dto.ExperimentStatusDetailResponse;
import com.fund.stockProject.experiment.dto.ExperimentStatusDetailResponse.TradeInfo;
import com.fund.stockProject.experiment.dto.ExperimentStatusResponse;
import com.fund.stockProject.experiment.dto.ExperimentInfoResponse;
import com.fund.stockProject.experiment.dto.HumanIndicatorDistributionResponse;
import com.fund.stockProject.experiment.dto.ReportPatternDto;
import com.fund.stockProject.experiment.dto.ReportStatisticDto;
import com.fund.stockProject.experiment.entity.Experiment;
import com.fund.stockProject.experiment.entity.ExperimentTradeItem;
import com.fund.stockProject.experiment.repository.ExperimentRepository;
import com.fund.stockProject.experiment.repository.ExperimentTradeItemRepository;
import com.fund.stockProject.score.entity.Score;
import com.fund.stockProject.score.repository.ScoreRepository;
import com.fund.stockProject.security.principle.CustomUserDetails;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.domain.EXCHANGENUM;
import com.fund.stockProject.stock.dto.response.StockInfoResponse;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.repository.StockQueryRepository;
import com.fund.stockProject.stock.repository.StockRepository;
import com.fund.stockProject.stock.service.SecurityService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fund.stockProject.portfolio.dto.PortfolioResultResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExperimentService {

    private final ExperimentRepository experimentRepository;
    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final ScoreRepository scoreRepository;
    private final SecurityService securityService;
    private final StockQueryRepository stockQueryRepository;
    private final ExperimentTradeItemRepository experimentTradeItemRepository;
    private final HolidayService holidayService;

    /*
     * 실험실 - 매수 현황
     * */
    public ExperimentStatusResponse getExperimentStatus(final CustomUserDetails customUserDetails) {
        // 로그인한 유저 관련 모의 투자 정보 조회
final List<Experiment> experimentsByUserId = experimentRepository.findExperimentsByEmail(
    customUserDetails.getEmail());

        if (experimentsByUserId.isEmpty()) {
            // 빈 실험일 때도 기본값을 가진 응답 반환 (에러가 아닌 빈 상태)
            return ExperimentStatusResponse.builder()
                .progressExperiments(new ArrayList<>())
                .completeExperiments(new ArrayList<>())
                .avgRoi(0.0)
                .totalTradeCount(0)
                .progressTradeCount(0)
                .successRate(0.0)
                .build();
        }

        // 진행중인 모의 투자 종목
        final List<ExperimentInfoResponse> progressExperimentsInfo = new ArrayList<>();
        // 완료된 모의 투자 종목
        final List<ExperimentInfoResponse> completeExperimentsInfo = new ArrayList<>();

        // 진행중인 실험 수를 DB에서 직접 조회 (Experiment 엔티티의 status 필드 기반, StockInfo 조회와 무관)
        final int countByStatusProgress = experimentRepository.countExperimentsByEmailAndStatus(
            customUserDetails.getEmail(), "PROGRESS");

        // 로그인한 유저 관련 모의 투자 정보 조회 진행/완료 리스트에 저장
        for (final Experiment experiment : experimentsByUserId) {
            final Optional<Stock> bySymbol = stockRepository.findBySymbol(experiment.getStock().getSymbol());

            if (bySymbol.isEmpty()) {
                log.warn("Stock not found for symbol: {}, experimentId: {}", 
                    experiment.getStock().getSymbol(), experiment.getId());
                continue; // 해당 실험을 건너뛰고 다음 실험 처리
            }

            final Stock stock = bySymbol.get();

            // StockInfo 조회 시 타임아웃이나 에러 처리
            StockInfoResponse stockInfoKorea = null;
            COUNTRY country = getCountryFromExchangeNum(stock.getExchangeNum()); // 기본값 설정
            try {
                stockInfoKorea = securityService.getSecurityStockInfoKorea(
                    stock.getId(),
                    stock.getSymbolName(),
                    stock.getSecurityName(),
                    stock.getSymbol(),
                    stock.getExchangeNum(),
                    country
                ).block();
                
                if (stockInfoKorea != null && stockInfoKorea.getCountry() != null) {
                    country = stockInfoKorea.getCountry();
                }
            } catch (Exception e) {
                log.warn("Failed to get StockInfo for stockId: {}, experimentId: {}, will use default values", 
                    stock.getId(), experiment.getId(), e);
                // StockInfo 조회 실패해도 실험 정보는 포함시킴
            }

            // 매수 시점 점수
            final int buyScore = experiment.getScore();
            
            // 현재 시점 점수 조회: 최근 ExperimentTradeItem이 있으면 그것의 score 사용, 없으면 Score 테이블에서 최신 점수 조회
            int currentScore = buyScore; // 기본값은 매수 시점 점수
            final List<ExperimentTradeItem> tradeItems = experimentTradeItemRepository.findExperimentTradeItemsByExperimentId(experiment.getId());
            if (!tradeItems.isEmpty()) {
                // 가장 최근 trade item의 score 사용
                currentScore = tradeItems.get(tradeItems.size() - 1).getScore();
            } else if (stockInfoKorea != null) {
                // trade item이 없으면 Score 테이블에서 최신 점수 조회
                try {
                    final Optional<Score> scoreOptional = scoreRepository.findByStockIdAndDate(stock.getId(), LocalDate.now());
                    if (scoreOptional.isEmpty()) {
                        final Optional<Score> latestScoreOptional = scoreRepository.findTopByStockIdOrderByDateDesc(stock.getId());
                        if (latestScoreOptional.isPresent()) {
                            final Score latestScore = latestScoreOptional.get();
                            if (country.equals(COUNTRY.KOREA)) {
                                currentScore = latestScore.getScoreKorea();
                            } else {
                                currentScore = latestScore.getScoreOversea();
                            }
                        }
                    } else {
                        final Score todayScore = scoreOptional.get();
                        if (country.equals(COUNTRY.KOREA)) {
                            currentScore = todayScore.getScoreKorea();
                        } else {
                            currentScore = todayScore.getScoreOversea();
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to get current score for stockId: {}, experimentId: {}", 
                        stock.getId(), experiment.getId(), e);
                    // currentScore는 buyScore로 유지
                }
            }
            
            // 현재 가격
            final Double currentPrice = (stockInfoKorea != null && stockInfoKorea.getPrice() != null)
                ? stockInfoKorea.getPrice()
                : experiment.getBuyPrice();

            if (experiment.getStatus().equals("PROGRESS")) {
                progressExperimentsInfo.add(ExperimentInfoResponse.builder()
                    .experimentId(experiment.getId())
                    .roi(experiment.getRoi())
                    .buyAt(experiment.getBuyAt())
                    .buyPrice(experiment.getBuyPrice())
                    .symbolName(stock.getSymbolName())
                    .status(experiment.getStatus())
                    .country(country)
                    .buyScore(buyScore)
                    .currentScore(currentScore)
                    .currentPrice(currentPrice)
                    .stockId(stock.getId())
                    .build());

                continue;
            }

            completeExperimentsInfo.add(ExperimentInfoResponse.builder()
                .experimentId(experiment.getId())
                .roi(experiment.getRoi())
                .buyAt(experiment.getBuyAt())
                .buyPrice(experiment.getBuyPrice())
                .symbolName(stock.getSymbolName())
                .status(experiment.getStatus())
                .country(country)
                .buyScore(buyScore)
                .currentScore(currentScore)
                .currentPrice(currentPrice)
                .stockId(stock.getId())
                .build());
        }

        final double averageRoi = experimentsByUserId.stream()
            .mapToDouble(Experiment::getRoi) // 각 ROI 값을 double로 추출
            .average()                            // OptionalDouble 반환
            .orElse(0.0);

        // 완료된 실험 중 ROI > 0인 실험 수 계산
        final long completedCount = experimentsByUserId.stream().filter(p -> "COMPLETE".equals(p.getStatus())).count();
        final long profitCount = experimentsByUserId.stream().filter(p -> "COMPLETE".equals(p.getStatus()) && p.getRoi() != null && p.getRoi() > 0).count();
        double successRate = completedCount > 0 ? ((double) profitCount / completedCount) * 100 : 0.0;

        return ExperimentStatusResponse.builder()
            .progressExperiments(progressExperimentsInfo) // 진행중인 실험 정보
            .completeExperiments(completeExperimentsInfo) // 완료된 실험 정보
            .avgRoi(averageRoi) // 평균 수익률
            .totalTradeCount(experimentsByUserId.size()) // 총 실험 수 (전체 모의투자 개수)
            .progressTradeCount(countByStatusProgress) // 진행중인 실험 수 (사용자별 진행중인 모의투자 개수)
            .successRate(successRate) // 성공률
            .build();
    }

    /*
     * 실험실 - 종목 매수 현황 자세히 보기
     * */
    public ExperimentStatusDetailResponse getExperimentStatusDetail(final Integer experimentId) {
        // 자세히 보기 선택한 실험 데이터 조회
        final Optional<Experiment> experimentOptional = experimentRepository.findExperimentByExperimentId(experimentId);
        if (experimentOptional.isEmpty()) {
            log.warn("Experiment not found - experimentId: {}", experimentId);
            throw new NoSuchElementException("실험을 찾을 수 없습니다");
        }
        final Experiment experiment = experimentOptional.get();
        
        // 실험 데이터에 해당하는 자동 모의 실험 내역 조회
        final List<ExperimentTradeItem> experimentTradeItems = experimentTradeItemRepository.findExperimentTradeItemsByExperimentId(experimentId);
        
        // 매수 시점 점수
        final int buyScore = experiment.getScore();
        
        // 현재 시점 점수와 현재 가격 조회
        int currentScore = buyScore;
        Double currentPrice = experiment.getBuyPrice();
        
        // StockInfo 조회
        final Stock stock = experiment.getStock();
        StockInfoResponse stockInfo = null;
        try {
            stockInfo = securityService.getSecurityStockInfoKorea(
                stock.getId(),
                stock.getSymbolName(),
                stock.getSecurityName(),
                stock.getSymbol(),
                stock.getExchangeNum(),
                getCountryFromExchangeNum(stock.getExchangeNum())
            ).block();
            
            if (stockInfo != null && stockInfo.getPrice() != null) {
                currentPrice = stockInfo.getPrice();
            }
        } catch (Exception e) {
            log.warn("Failed to get StockInfo for experimentId: {}", experimentId, e);
        }
        
        // experimentTradeItems가 비어있을 수 있음
        if (experimentTradeItems.isEmpty()) {
            log.warn("No trade items found for experimentId: {}", experimentId);
            
            // trade item이 없으면 Score 테이블에서 최신 점수 조회
            try {
                final Optional<Score> scoreOptional = scoreRepository.findByStockIdAndDate(stock.getId(), LocalDate.now());
                if (scoreOptional.isEmpty()) {
                    final Optional<Score> latestScoreOptional = scoreRepository.findTopByStockIdOrderByDateDesc(stock.getId());
                    if (latestScoreOptional.isPresent()) {
                        final Score latestScore = latestScoreOptional.get();
                        final COUNTRY country = stockInfo != null ? stockInfo.getCountry() : getCountryFromExchangeNum(stock.getExchangeNum());
                        if (country.equals(COUNTRY.KOREA)) {
                            currentScore = latestScore.getScoreKorea();
                        } else {
                            currentScore = latestScore.getScoreOversea();
                        }
                    }
                } else {
                    final Score todayScore = scoreOptional.get();
                    final COUNTRY country = stockInfo != null ? stockInfo.getCountry() : getCountryFromExchangeNum(stock.getExchangeNum());
                    if (country.equals(COUNTRY.KOREA)) {
                        currentScore = todayScore.getScoreKorea();
                    } else {
                        currentScore = todayScore.getScoreOversea();
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get current score for experimentId: {}", experimentId, e);
            }
            
            // 최종 수익률 계산: 현재 가격 기준
            double roi;
            if (experiment.getBuyPrice() == null || experiment.getBuyPrice() == 0.0) {
                roi = 0.0;
            } else {
                roi = ((currentPrice - experiment.getBuyPrice()) / experiment.getBuyPrice()) * 100;
            }
            
            // country 설정
            final COUNTRY country = stockInfo != null ? stockInfo.getCountry() : getCountryFromExchangeNum(stock.getExchangeNum());
            
            return ExperimentStatusDetailResponse.builder()
                .tradeInfos(new ArrayList<>())
                .roi(roi)
                .status(experiment.getStatus())
                .symbolName(experiment.getStock().getSymbolName())
                .stockId(stock.getId())
                .buyScore(buyScore)
                .currentScore(currentScore)
                .buyPrice(experiment.getBuyPrice())
                .currentPrice(currentPrice)
                .buyAt(experiment.getBuyAt())
                .country(country)
                .build();
        }
        
        // 가장 최근 수익률 조회
        final ExperimentTradeItem recentExperimentTradeItem = experimentTradeItems.get(experimentTradeItems.size() - 1);
        currentScore = recentExperimentTradeItem.getScore();
        if (recentExperimentTradeItem.getPrice() != null) {
            currentPrice = recentExperimentTradeItem.getPrice();
        }

        // 최종 수익률 계산:  ((현재가 - 매수가) / 매수가) * 100
        double roi;
        if (experiment.getBuyPrice() == null || experiment.getBuyPrice() == 0.0) {
            roi = 0.0;
        } else {
            roi = ((currentPrice - experiment.getBuyPrice()) / experiment.getBuyPrice()) * 100;
        }

        final List<TradeInfo> tradeInfos = new ArrayList<>();

        for (final ExperimentTradeItem experimentTradeItem : experimentTradeItems) {
            tradeInfos.add(TradeInfo.builder()
                .price(experimentTradeItem.getPrice())
                .tradeAt(experimentTradeItem.getTradeAt())
                .score(experimentTradeItem.getScore())
                .roi(experimentTradeItem.getRoi())
                .build()
            );
        }

        // country 설정
        final COUNTRY country = stockInfo != null ? stockInfo.getCountry() : getCountryFromExchangeNum(stock.getExchangeNum());

        return ExperimentStatusDetailResponse.builder()
            .tradeInfos(tradeInfos)
            .roi(roi)
            .status(experiment.getStatus())
            .symbolName(experiment.getStock().getSymbolName())
            .stockId(stock.getId())
            .buyScore(buyScore)
            .currentScore(currentScore)
            .buyPrice(experiment.getBuyPrice())
            .currentPrice(currentPrice)
            .buyAt(experiment.getBuyAt())
            .country(country)
            .build();
    }

    private COUNTRY getCountryFromExchangeNum(EXCHANGENUM exchangenum) {
        if (exchangenum == null) {
            log.warn("ExchangeNum is null, defaulting to KOREA");
            return COUNTRY.KOREA; // 기본값
        }
        return List.of(EXCHANGENUM.KOSPI, EXCHANGENUM.KOSDAQ, EXCHANGENUM.KOREAN_ETF).contains(exchangenum) ? COUNTRY.KOREA : COUNTRY.OVERSEA;
    }

    @Transactional
    public ExperimentSimpleResponse buyExperiment(final CustomUserDetails customUserDetails, final Integer stockId, String country) {
        // Stock 조회 및 검증
        final Optional<Stock> stockById = stockRepository.findStockById(stockId);
        if (stockById.isEmpty()) {
            log.warn("Stock not found - stockId: {}", stockId);
            return ExperimentSimpleResponse.builder()
                .message("종목을 찾을 수 없습니다")
                .success(false)
                .price(0.0d)
                .build();
        }
        final Stock stock = stockById.get();

        // User 조회 및 검증
        final Optional<User> userById = userRepository.findByEmail(customUserDetails.getEmail());
        if (userById.isEmpty()) {
            log.warn("User not found - email: {}", customUserDetails.getEmail());
            return ExperimentSimpleResponse.builder()
                .message("사용자 정보를 찾을 수 없습니다")
                .success(false)
                .price(0.0d)
                .build();
        }
        final User user = userById.get();

        final LocalDateTime now = LocalDateTime.now(); // 현재 날짜와 시간
        final LocalTime current = now.toLocalTime(); // 현재 시간
        final LocalDateTime startOfToday = now.toLocalDate().atStartOfDay(); // 오늘 시작 시간
        final LocalDateTime endOfToday = now.toLocalDate().atTime(LocalTime.MAX); // 오늘 마지막 시간
        final DayOfWeek dayOfWeek = now.getDayOfWeek(); // 요일
        Double price = 0.0d;

        // 진행 중인 동일 종목 실험이 있으면 중복 매수 불가
        Optional<Experiment> progressExperiment = experimentRepository.findProgressExperimentByUserAndStock(
            user.getId(), stockId);
        if (progressExperiment.isPresent()) {
            return ExperimentSimpleResponse.builder()
                .message("진행 중인 동일 종목이 있습니다")
                .success(false)
                .price(0.0d)
                .build();
        }

        // Score 조회 및 검증 - 오늘 날짜 우선, 없으면 최신 점수 사용
        Optional<Score> scoreOptional = scoreRepository.findByStockIdAndDate(stockId, LocalDate.now());
        if (scoreOptional.isEmpty()) {
            log.warn("Today's score not found for stock - stockId: {}, trying latest score", stockId);
            // 오늘 날짜 점수가 없으면 최신 점수 조회 시도
            scoreOptional = scoreRepository.findTopByStockIdOrderByDateDesc(stockId);
            if (scoreOptional.isEmpty()) {
                log.error("No score found for stock - stockId: {}", stockId);
                return ExperimentSimpleResponse.builder()
                    .message("점수 정보를 찾을 수 없습니다")
                    .success(false)
                    .price(0.0d)
                    .build();
            }
            log.info("Using latest score instead of today's score for stockId: {}", stockId);
        }
        final Score findByStockIdAndDate = scoreOptional.get();
        int score = 9999;

        // 차트에서 이미 작동하는 getSecurityStockInfoKorea 사용 (inquire-price API)
        // inquire-price-2는 작동하지 않으므로 inquire-price로 변경
        // StockInfo 조회 - 차트와 동일한 방식으로 .block() 사용
        StockInfoResponse stockInfoResponse;
        try {
            stockInfoResponse = securityService.getSecurityStockInfoKorea(
                stock.getId(),
                stock.getSymbolName(),
                stock.getSecurityName(),
                stock.getSymbol(),
                stock.getExchangeNum(),
                getCountryFromExchangeNum(stock.getExchangeNum())
            ).block();
        } catch (Exception e) {
            log.warn("Failed to get StockInfo for stock - stockId: {}, error: {}", stockId, e.getMessage());
            return ExperimentSimpleResponse.builder()
                .message("주가 정보를 가져올 수 없습니다")
                .success(false)
                .price(0.0d)
                .build();
        }

        if (stockInfoResponse.getCountry().equals(COUNTRY.KOREA)) {
            score = findByStockIdAndDate.getScoreKorea();
            final Optional<Experiment> experimentByStockIdAndBuyAt = experimentRepository.findExperimentByStockIdForTodayAndUser(
                stockId, user.getId(), startOfToday, endOfToday);

            // 하루에 같은 종목 중복 구매 불가 처리
            if (experimentByStockIdAndBuyAt.isPresent()) {
                return ExperimentSimpleResponse.builder()
                    .message("같은 종목 중복 구매")
                    .success(false)
                    .price(0.0d)
                    .build();
            }

            LocalTime koreaEndTime = LocalTime.of(17, 0);

            // 종가 결정 - null 체크 추가
            if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                price = stockInfoResponse.getYesterdayPrice() != null ? stockInfoResponse.getYesterdayPrice() : stockInfoResponse.getPrice();
            } else {
                Double selectedPrice = current.isBefore(koreaEndTime) ? stockInfoResponse.getYesterdayPrice() : stockInfoResponse.getPrice();
                price = selectedPrice != null ? selectedPrice : (stockInfoResponse.getPrice() != null ? stockInfoResponse.getPrice() : stockInfoResponse.getYesterdayPrice());
            }
            
            // 최종적으로도 null이면 에러
            if (price == null) {
                log.error("Price is null for stock - stockId: {}, stockInfo: price={}, yesterdayPrice={}", 
                    stockId, stockInfoResponse.getPrice(), stockInfoResponse.getYesterdayPrice());
                return ExperimentSimpleResponse.builder()
                    .message("주가 정보를 가져올 수 없습니다")
                    .success(false)
                    .price(0.0d)
                    .build();
            }
        } else {
            // 해외 주식 로직
            score = findByStockIdAndDate.getScoreOversea();

            // 해당 구간에 이미 매수한 경우 중복 매수 방지
            Optional<Experiment> existingItem = experimentRepository.findExperimentByStockIdForTodayAndUser(
                stockId, user.getId(), startOfToday, endOfToday);

            if (existingItem.isPresent()) {
                return ExperimentSimpleResponse.builder()
                    .message("같은 종목 중복 구매")
                    .success(false)
                    .price(0.0d)
                    .build();
            }

            LocalTime overseasEndTime = LocalTime.of(6, 0);
            
            // 종가 결정 - null 체크 추가
            if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                price = stockInfoResponse.getYesterdayPrice() != null ? stockInfoResponse.getYesterdayPrice() : stockInfoResponse.getPrice();
            } else {
                Double selectedPrice = current.isBefore(overseasEndTime) ? stockInfoResponse.getYesterdayPrice() : stockInfoResponse.getPrice();
                price = selectedPrice != null ? selectedPrice : (stockInfoResponse.getPrice() != null ? stockInfoResponse.getPrice() : stockInfoResponse.getYesterdayPrice());
            }
            
            // 최종적으로도 null이면 에러
            if (price == null) {
                log.error("Price is null for overseas stock - stockId: {}, stockInfo: price={}, yesterdayPrice={}", 
                    stockId, stockInfoResponse.getPrice(), stockInfoResponse.getYesterdayPrice());
                return ExperimentSimpleResponse.builder()
                    .message("주가 정보를 가져올 수 없습니다")
                    .success(false)
                    .price(0.0d)
                    .build();
            }
        }

        List<Score> scores = stock.getScores();

        // 저장할 실험 데이터 생성
        final Experiment experiment = Experiment.builder()
            .user(user)
            .stock(stock)
            .status("PROGRESS")
            .buyAt(now)
            .buyPrice(price)
            .roi(0.0d)  // 매수 시점에는 ROI 0%
            .score(score)
            .build();

        // 모의 매수한 실험 데이터 저장
        experimentRepository.save(experiment);

        final ExperimentTradeItem experimentTradeItem = ExperimentTradeItem.builder()
            .experiment(experiment)
            .price(price)
            .roi(0.0d)
            .score(experiment.getScore())
            .tradeAt(now)
            .build();

        experimentTradeItemRepository.save(experimentTradeItem);

        return ExperimentSimpleResponse.builder()
            .message("모의 매수 성공")
            .success(true)
            .price(price)
            .build();
    }

    // 매수결과 조회
    public ExperimentReportResponse getReport(CustomUserDetails customUserDetails) {
        final String email = customUserDetails.getEmail();

        // 인간지표 점수대별 평균 수익률
        final List<ReportStatisticDto> reportStatisticDtos = new ArrayList<>();

        // 1. 60점 이하 평균 수익률
        final double totalAvgRoi_0_59 = experimentRepository.findTotalAvgRoi(0, 59);
        final Double userAvgRoi_0_59 = experimentRepository.findUserAvgRoi(0, 59, email);

        reportStatisticDtos.add(ReportStatisticDto.builder()
            .totalAvgRoi(totalAvgRoi_0_59)
            .userAvgRoi(userAvgRoi_0_59 != null ? userAvgRoi_0_59 : 0.0)
            .scoreRange(SCORERANGE.RANGE_0_59.getRange())
            .build());

        // 2. 60~69점 평균 수익률
        final double totalAvgRoi_60_69 = experimentRepository.findTotalAvgRoi(60, 69);
        final Double userAvgRoi_60_69 = experimentRepository.findUserAvgRoi(60, 69, email);

        reportStatisticDtos.add(ReportStatisticDto.builder()
            .totalAvgRoi(totalAvgRoi_60_69)
            .userAvgRoi(userAvgRoi_60_69 != null ? userAvgRoi_60_69 : 0.0)
            .scoreRange(SCORERANGE.RANGE_60_69.getRange())
            .build());

        // 3. 70~79점 평균 수익률
        final double totalAvgRoi_70_79 = experimentRepository.findTotalAvgRoi(70, 79);
        final Double userAvgRoi_70_79 = experimentRepository.findUserAvgRoi(70, 79, email);

        reportStatisticDtos.add(ReportStatisticDto.builder()
            .totalAvgRoi(totalAvgRoi_70_79)
            .userAvgRoi(userAvgRoi_70_79 != null ? userAvgRoi_70_79 : 0.0)
            .scoreRange(SCORERANGE.RANGE_70_79.getRange())
            .build());

        // 3. 80~89점 평균 수익률
        final double totalAvgRoi_80_89 = experimentRepository.findTotalAvgRoi(80, 89);
        final Double userAvgRoi_80_89 = experimentRepository.findUserAvgRoi(80, 89, email);

        reportStatisticDtos.add(ReportStatisticDto.builder()
            .totalAvgRoi(totalAvgRoi_80_89)
            .userAvgRoi(userAvgRoi_80_89 != null ? userAvgRoi_80_89 : 0.0)
            .scoreRange(SCORERANGE.RANGE_80_89.getRange())
            .build());

        // 5. 90이상 평균 수익률
        final double totalAvgRoi_90_100 = experimentRepository.findTotalAvgRoi(90, 100);
        final Double userAvgRoi_90_100 = experimentRepository.findUserAvgRoi(90, 100, email);

        reportStatisticDtos.add(ReportStatisticDto.builder()
            .totalAvgRoi(totalAvgRoi_90_100)
            .userAvgRoi(userAvgRoi_90_100 != null ? userAvgRoi_90_100 : 0.0)
            .scoreRange(SCORERANGE.RANGE_90_100.getRange())
            .build());

        LocalDateTime now = LocalDateTime.now();

        // 이번 주 월요일 00:00:00
        LocalDateTime startOfWeek = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .toLocalDate()
            .atStartOfDay();

        // 이번 주 금요일 23:59:59.999999999
        LocalDateTime endOfWeek = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
            .toLocalDate()
            .atTime(LocalTime.MAX);

        // 이번주 진행 실험 횟수
        final int weeklyExperimentCount = experimentRepository.countExperimentsForWeek(
            startOfWeek, endOfWeek);

        // 특정 사용자가 진행한 총 실험
        final List<Experiment> experimentsByEmailAndStatus = experimentRepository.findExperimentsByEmailAndStatus(
            email, "COMPLETE");

        // 전체 진행한 실험 개수
        long totalExperimentCount = experimentsByEmailAndStatus.size();

        // 성공한 수익률 데이터
        long successExperimentCount = experimentsByEmailAndStatus.stream()
            .filter(experiment -> experiment.getRoi() > 0).count();

        // 유저 성공률 데이터 조회
        final double successExperimentRate = experimentRepository.findSuccessExperimentRate(email);
        int startRange = 0;
        int endRange;

        if (0 < successExperimentRate && successExperimentRate <= 20) {
            endRange = 20;
        } else if (successExperimentRate <= 40) {
            startRange = 21;
            endRange = 40;
        } else if (successExperimentRate <= 60) {
            startRange = 41;
            endRange = 60;
        } else if (successExperimentRate <= 80) {
            startRange = 61;
            endRange = 80;
        } else {
            startRange = 81;
            endRange = 100;
        }

        // 동일 등급 전체 유저 비율 계산
        final int countSameGradeUser = experimentRepository.countSameGradeUser(startRange, endRange);
        final long completedUserCount = experimentRepository.countUsersWithCompletedExperiments();
        long sameGradeUserRage = completedUserCount == 0 ? 0 : (countSameGradeUser * 100L / completedUserCount);

        final List<ReportPatternDto> reportPatternDtos = new ArrayList<>();

        // 인간지표 점수 별 투자 유형 패턴 데이터
        final List<Object[]> experimentGroupByBuyAt = experimentRepository.findExperimentGroupByBuyAt();

        for (final Object[] row : experimentGroupByBuyAt) {
            // Object[]에서 데이터 추출: [buy_date, avg_roi, avg_score]
            LocalDate buyDate = ((java.sql.Date) row[0]).toLocalDate();
            // ROUND() 함수는 BigDecimal을 반환하므로 doubleValue()로 변환
            Double avgRoi = row[1] instanceof java.math.BigDecimal 
                ? ((java.math.BigDecimal) row[1]).doubleValue() 
                : (Double) row[1];
            Double avgScore = row[2] instanceof java.math.BigDecimal 
                ? ((java.math.BigDecimal) row[2]).doubleValue() 
                : (Double) row[2];
            
            reportPatternDtos.add(
                ReportPatternDto.builder()
                    .score(avgScore.intValue())
                    .buyAt(buyDate.atStartOfDay())
                    .roi(avgRoi)
                    .build()
            );
        }

        // 투자 패턴 그래프 데이터
        return ExperimentReportResponse.builder()
            .weeklyExperimentCount(weeklyExperimentCount)
            .reportStatisticDtos(reportStatisticDtos)
            .sameGradeUserRate(sameGradeUserRage)
            .successUserExperiments(successExperimentCount)
            .totalUserExperiments(totalExperimentCount)
            .reportPatternDtos(reportPatternDtos)
            .build();
    }

    // Lab 결과 화면용 응답 (example.md 구조 기반)
    public PortfolioResultResponse getPortfolioResult(final CustomUserDetails customUserDetails) {
        final String email = customUserDetails.getEmail();

        // 완료된 실험 조회 (sellAt 내림차순 정렬)
        final List<Experiment> completed = experimentRepository.findExperimentsByEmailAndStatus(email, "COMPLETE")
            .stream()
            .sorted((a, b) -> {
                if (a.getSellAt() == null && b.getSellAt() == null) return 0;
                if (a.getSellAt() == null) return 1;
                if (b.getSellAt() == null) return -1;
                return b.getSellAt().compareTo(a.getSellAt());
            })
            .collect(java.util.stream.Collectors.toList());

        long totalCompleted = completed.size();
        long profitCount = completed.stream().filter(e -> e.getRoi() != null && e.getRoi() > 0).count();

        // 이번 주 실험 횟수 계산
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfWeek = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .toLocalDate()
            .atStartOfDay();
        LocalDateTime endOfWeek = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            .toLocalDate()
            .atTime(LocalTime.MAX);
        final int weeklyExperimentCount = experimentRepository.countExperimentsForWeekByUser(email, startOfWeek, endOfWeek);

        // 성공률 계산 (전체 완료된 실험 기준)
        double successRateVal = totalCompleted == 0 ? 0.0 : (profitCount * 100.0 / totalCompleted);

        // 동일 등급 전체 유저 비율 계산 (percentile)
        int startRange = 0;
        int endRange;
        if (successRateVal == 0) {
            startRange = 0;
            endRange = 0;
        } else if (successRateVal > 0 && successRateVal <= 20) {
            startRange = 0;
            endRange = 20;
        } else if (successRateVal > 20 && successRateVal <= 40) {
            startRange = 21;
            endRange = 40;
        } else if (successRateVal > 40 && successRateVal <= 60) {
            startRange = 41;
            endRange = 60;
        } else if (successRateVal > 60 && successRateVal <= 80) {
            startRange = 61;
            endRange = 80;
        } else {
            startRange = 81;
            endRange = 100;
        }
        final int countSameGradeUser = experimentRepository.countSameGradeUser(startRange, endRange);
        final long completedUserCount = experimentRepository.countUsersWithCompletedExperiments();
        int sameGradePercentile = completedUserCount == 0 ? 0 : (int) (countSameGradeUser * 100L / completedUserCount);

        Map<String, Integer> distribution = new LinkedHashMap<>();
        if (completedUserCount == 0) {
            distribution.put("worst", 0);
            distribution.put("bad", 0);
            distribution.put("normal", 0);
            distribution.put("good", 0);
            distribution.put("best", 0);
        } else {
            distribution.put("worst", (int) (experimentRepository.countUsersBySuccessRateRange(0, 20) * 100L / completedUserCount));
            distribution.put("bad", (int) (experimentRepository.countUsersBySuccessRateRange(20, 40) * 100L / completedUserCount));
            distribution.put("normal", (int) (experimentRepository.countUsersBySuccessRateRange(40, 60) * 100L / completedUserCount));
            distribution.put("good", (int) (experimentRepository.countUsersBySuccessRateRange(60, 80) * 100L / completedUserCount));
            distribution.put("best", (int) (experimentRepository.countUsersBySuccessRateAtLeast(80) * 100L / completedUserCount));
        }

        // 점수 구간별 사용자 평균 수익률 및 전체 유저 평균
        Double u_60_69 = experimentRepository.findUserAvgRoi(60, 69, email);
        Double u_70_79 = experimentRepository.findUserAvgRoi(70, 79, email);
        Double u_80_89 = experimentRepository.findUserAvgRoi(80, 89, email);
        Double u_90_100 = experimentRepository.findUserAvgRoi(90, 100, email);

        Double t_60_69 = experimentRepository.findTotalAvgRoi(60, 69);
        Double t_70_79 = experimentRepository.findTotalAvgRoi(70, 79);
        Double t_80_89 = experimentRepository.findTotalAvgRoi(80, 89);
        Double t_90_100 = experimentRepository.findTotalAvgRoi(90, 100);

        List<PortfolioResultResponse.ScoreTableItem> scoreTable = new ArrayList<>();
        scoreTable.add(PortfolioResultResponse.ScoreTableItem.builder().min(60).max(69).avgYieldTotal(t_60_69).avgYieldUser(u_60_69).build());
        scoreTable.add(PortfolioResultResponse.ScoreTableItem.builder().min(70).max(79).avgYieldTotal(t_70_79).avgYieldUser(u_70_79).build());
        scoreTable.add(PortfolioResultResponse.ScoreTableItem.builder().min(80).max(89).avgYieldTotal(t_80_89).avgYieldUser(u_80_89).build());
        scoreTable.add(PortfolioResultResponse.ScoreTableItem.builder().min(90).max(100).avgYieldTotal(t_90_100).avgYieldUser(u_90_100).build());

        BestWorstRangeScores bestWorstRangeScores = resolveBestWorstRangeScores(scoreTable);
        Integer bestYieldScore = bestWorstRangeScores.bestScore();
        Integer worstYieldScore = bestWorstRangeScores.worstScore();

        // HumanIndicator type 결정 (성공률 기반)
        String humanIndicatorType;
        if (totalCompleted == 0 || successRateVal <= 20) {
            humanIndicatorType = "worst";
        } else if (successRateVal <= 40) {
            humanIndicatorType = "bad";
        } else if (successRateVal <= 60) {
            humanIndicatorType = "normal";
        } else if (successRateVal <= 80) {
            humanIndicatorType = "good";
        } else {
            humanIndicatorType = "best";
        }

        // History 생성 (최근 10개, 완료된 실험)
        List<Experiment> recentExperiments = completed.stream()
            .limit(10)
            .collect(java.util.stream.Collectors.toList());

        // 종목명 중복 체크를 위한 카운트
        java.util.Map<String, Long> stockNameCount = recentExperiments.stream()
            .map(e -> e.getStock().getSecurityName())
            .collect(java.util.stream.Collectors.groupingBy(
                name -> name, 
                java.util.stream.Collectors.counting()
            ));

        List<PortfolioResultResponse.HistoryPoint> history = recentExperiments.stream()
            .map(e -> PortfolioResultResponse.HistoryPoint.builder()
                .date(PortfolioResultResponse.HistoryPoint.toDateLabel(e.getSellAt() != null ? e.getSellAt() : e.getBuyAt()))
                .score(e.getScore())
                .roi(roundTo1Decimal(e.getRoi() != null ? e.getRoi() : 0.0))
                .stockId(e.getStock().getId())
                .stockName(e.getStock().getSecurityName())
                .isDuplicateName(stockNameCount.get(e.getStock().getSecurityName()) > 1)
                .build())
            .collect(java.util.stream.Collectors.toList());

        // 패턴 결정 로직 (Vector Analysis - 원점 (50, 0) 기준 거리 합산)
        String patternType = "value-preemptive"; // 기본값
        double valuePreemptiveSum = 0;    // 2사분면: X < 50, Y > 0
        double trendPreemptiveSum = 0;    // 1사분면: X > 50, Y > 0
        double reverseInvestorSum = 0;    // 3사분면: X < 50, Y < 0
        double laggingFollowerSum = 0;    // 4사분면: X > 50, Y < 0

        for (PortfolioResultResponse.HistoryPoint point : history) {
            double dx = point.getScore() - 50.0;
            double dy = point.getRoi();
            double distance = Math.sqrt(dx * dx + dy * dy);

            if (point.getScore() < 50 && point.getRoi() > 0) {
                valuePreemptiveSum += distance;   // 가치 선점형
            } else if (point.getScore() >= 50 && point.getRoi() > 0) {
                trendPreemptiveSum += distance;   // 트렌드 선점형
            } else if (point.getScore() < 50 && point.getRoi() <= 0) {
                reverseInvestorSum += distance;   // 역행 투자형
            } else {
                laggingFollowerSum += distance;   // 후행 추종형
            }
        }

        double maxSum = Math.max(Math.max(valuePreemptiveSum, trendPreemptiveSum),
                                 Math.max(reverseInvestorSum, laggingFollowerSum));

        if (maxSum > 0) {
            if (maxSum == valuePreemptiveSum) {
                patternType = "value-preemptive";
            } else if (maxSum == trendPreemptiveSum) {
                patternType = "trend-preemptive";
            } else if (maxSum == reverseInvestorSum) {
                patternType = "reverse-investor";
            } else {
                patternType = "lagging-follower";
            }
        }

        // 패턴별 유저 비율 계산 (간단히 전체 유저 대비 동일 패턴 유저 비율로 가정)
        // 실제로는 DB에서 각 유저의 패턴을 계산해야 하지만, 여기서는 랜덤하게 분포한다고 가정하여 25%로 설정
        int patternPercentile = 25;

        // Response 빌드
        PortfolioResultResponse.Recommend recommend = PortfolioResultResponse.Recommend.builder()
            .weeklyExperimentCount(weeklyExperimentCount)
            .bestYieldScore(bestYieldScore)
            .worstYieldScore(worstYieldScore)
            .scoreTable(scoreTable)
            .build();

        PortfolioResultResponse.HumanIndicator humanIndicator = PortfolioResultResponse.HumanIndicator.builder()
            .type(humanIndicatorType)
            .percentile(sameGradePercentile)
            .successRate(Math.round(successRateVal * 10) / 10.0)
            .totalBuyCount((int) totalCompleted)
            .successCount((int) profitCount)
            .distribution(distribution)
            .build();

        PortfolioResultResponse.Pattern pattern = PortfolioResultResponse.Pattern.builder()
            .type(patternType)
            .percentile(patternPercentile)
            .history(history)
            .build();

        return PortfolioResultResponse.builder()
            .recommend(recommend)
            .humanIndicator(humanIndicator)
            .pattern(pattern)
            .build();
    }

    @Transactional(readOnly = true)
    public HumanIndicatorDistributionResponse getHumanIndicatorDistribution() {
        final long completedUserCount = experimentRepository.countUsersWithCompletedExperiments();
        Map<String, Integer> distribution = new LinkedHashMap<>();
        if (completedUserCount == 0) {
            distribution.put("worst", 0);
            distribution.put("bad", 0);
            distribution.put("normal", 0);
            distribution.put("good", 0);
            distribution.put("best", 0);
        } else {
            distribution.put("worst", (int) (experimentRepository.countSameGradeUser(0, 20) * 100L / completedUserCount));
            distribution.put("bad", (int) (experimentRepository.countSameGradeUser(21, 40) * 100L / completedUserCount));
            distribution.put("normal", (int) (experimentRepository.countSameGradeUser(41, 60) * 100L / completedUserCount));
            distribution.put("good", (int) (experimentRepository.countSameGradeUser(61, 80) * 100L / completedUserCount));
            distribution.put("best", (int) (experimentRepository.countSameGradeUser(81, 100) * 100L / completedUserCount));
        }

        return HumanIndicatorDistributionResponse.builder()
            .totalUsers(completedUserCount)
            .distribution(distribution)
            .build();
    }

    private BestWorstRangeScores resolveBestWorstRangeScores(List<PortfolioResultResponse.ScoreTableItem> scoreTable) {
        if (scoreTable == null || scoreTable.isEmpty()) {
            return new BestWorstRangeScores(null, null);
        }

        ScoreTableItemRange bestRange = null;
        ScoreTableItemRange worstRange = null;

        List<PortfolioResultResponse.ScoreTableItem> userItems = filterByMetric(scoreTable, true);
        if (!userItems.isEmpty()) {
            bestRange = toRange(selectBestRange(userItems, true));
            worstRange = toRange(selectWorstRange(excludeRange(userItems, bestRange), true));
        }

        if (bestRange == null) {
            List<PortfolioResultResponse.ScoreTableItem> totalItems = filterByMetric(scoreTable, false);
            bestRange = toRange(selectBestRange(totalItems, false));
        }

        if (worstRange == null) {
            List<PortfolioResultResponse.ScoreTableItem> totalItems = filterByMetric(scoreTable, false);
            worstRange = toRange(selectWorstRange(excludeRange(totalItems, bestRange), false));
        }

        Integer bestScore = bestRange != null ? bestRange.min() : null;
        Integer worstScore = worstRange != null ? worstRange.min() : null;
        return new BestWorstRangeScores(bestScore, worstScore);
    }

    private List<PortfolioResultResponse.ScoreTableItem> filterByMetric(
        List<PortfolioResultResponse.ScoreTableItem> scoreTable,
        boolean useUser
    ) {
        return scoreTable.stream()
            .filter(item -> getMetric(item, useUser) != null)
            .toList();
    }

    private PortfolioResultResponse.ScoreTableItem selectBestRange(
        List<PortfolioResultResponse.ScoreTableItem> items,
        boolean useUser
    ) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.stream()
            .max(java.util.Comparator.<PortfolioResultResponse.ScoreTableItem>comparingDouble(
                    item -> getMetric(item, useUser))
                .thenComparingInt(PortfolioResultResponse.ScoreTableItem::getMin))
            .orElse(null);
    }

    private PortfolioResultResponse.ScoreTableItem selectWorstRange(
        List<PortfolioResultResponse.ScoreTableItem> items,
        boolean useUser
    ) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.stream()
            .min(java.util.Comparator.<PortfolioResultResponse.ScoreTableItem>comparingDouble(
                    item -> getMetric(item, useUser))
                .thenComparingInt(PortfolioResultResponse.ScoreTableItem::getMin))
            .orElse(null);
    }

    private List<PortfolioResultResponse.ScoreTableItem> excludeRange(
        List<PortfolioResultResponse.ScoreTableItem> items,
        ScoreTableItemRange range
    ) {
        if (items == null || items.isEmpty() || range == null) {
            return items;
        }
        return items.stream()
            .filter(item -> item.getMin() != range.min() || item.getMax() != range.max())
            .toList();
    }

    private Double getMetric(PortfolioResultResponse.ScoreTableItem item, boolean useUser) {
        return useUser ? item.getAvgYieldUser() : item.getAvgYieldTotal();
    }

    private ScoreTableItemRange toRange(PortfolioResultResponse.ScoreTableItem item) {
        if (item == null) {
            return null;
        }
        return new ScoreTableItemRange(item.getMin(), item.getMax());
    }

    private record ScoreTableItemRange(int min, int max) { }

    private record BestWorstRangeScores(Integer bestScore, Integer worstScore) { }

    private String toScoreRangeLabel(int score) {
        if (score <= 59) return "60점 이하";
        if (score <= 69) return "60-69";
        if (score <= 79) return "70-79";
        if (score <= 89) return "80-89";
        return "90+";
    }

    private double roundTo1Decimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    // 영업일 기준 실험 진행한 기간이 5일 이상 지난 실험 데이터 조회
    @Transactional(readOnly = true)
    public List<Experiment> findExperimentsAfter5BusinessDays() {
        LocalDate fiveBusinessDaysAgo = calculatePreviousBusinessDate(LocalDate.now());
        // 5영업일 전 날짜의 마지막 시간까지의 실험을 모두 조회 (5영업일 이상 지난 모든 실험)
        LocalDateTime endDate = fiveBusinessDaysAgo.atTime(LocalTime.MAX);

        final List<Experiment> ExperimentsAfter5BusinessDays = experimentRepository.findExperimentsAfterFiveDays(endDate);

        if (ExperimentsAfter5BusinessDays.isEmpty()) {
            return new ArrayList<>();
        }

        // Stock이 제대로 로드되었는지 검증
        int nullStockCount = 0;
        for (Experiment exp : ExperimentsAfter5BusinessDays) {
            try {
                Stock stock = exp.getStock();
                if (stock == null) {
                    nullStockCount++;
                    log.error("Stock is null after fetch - experimentId: {}", exp.getId());
                } else {
                    // Stock 필드 접근하여 프록시 초기화 확인
                    Integer stockId = stock.getId();
                    if (stockId == null) {
                        nullStockCount++;
                        log.error("Stock.id is null after fetch - experimentId: {}", exp.getId());
                    }
                }
            } catch (Exception e) {
                nullStockCount++;
                log.error("Failed to access Stock for experiment - experimentId: {}, error: {}", exp.getId(), e.getMessage());
            }
        }
        
        if (nullStockCount > 0) {
            log.warn("Found {} experiments with null or inaccessible Stock out of {} total", 
                    nullStockCount, ExperimentsAfter5BusinessDays.size());
        }

        log.info("Found {} experiments that have passed 5 business days ({} with valid Stock)", 
                ExperimentsAfter5BusinessDays.size(), ExperimentsAfter5BusinessDays.size() - nullStockCount);
        return ExperimentsAfter5BusinessDays;
    }

    // 5영업일 전 날짜 찾는 함수 (주말 및 공휴일 제외)
    private LocalDate calculatePreviousBusinessDate(LocalDate fromDate) {
        int daysCounted = 0;
        LocalDate date = fromDate;

        while (daysCounted < 5) {
            date = date.minusDays(1);
            DayOfWeek day = date.getDayOfWeek();

            // 주말 제외
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                // 공휴일 제외 (API로 확인)
                if (!holidayService.isHolidaySync(date)) {
                    daysCounted++;
                }
            }
        }

        return date;
    }

    // 자동판매 - 실험 데이터 수정
    @Transactional
    public boolean updateExperiment(Experiment experiment) {
        try {
            // 이미 완료된 실험은 스킵
            if (!"PROGRESS".equals(experiment.getStatus())) {
                log.info("Skipping already completed experiment - experimentId: {}, status: {}", 
                        experiment.getId(), experiment.getStatus());
                return true; // 스킵은 성공으로 간주
            }
            
            // Stock이 null인 경우 실패
            final Stock stock = experiment.getStock();
            if (stock == null) {
                log.error("Stock is null for experiment - experimentId: {}", experiment.getId());
                return false;
            }
            
            // Stock 필수 필드 검증
            if (stock.getId() == null || stock.getSymbol() == null || stock.getExchangeNum() == null) {
                log.error("Stock has null required fields for experiment - experimentId: {}, stockId: {}, symbol: {}, exchangeNum: {}", 
                        experiment.getId(), stock.getId(), stock.getSymbol(), stock.getExchangeNum());
                return false;
            }
            
            log.info("Starting auto-sell update for experiment - experimentId: {}, stockId: {}, symbol: {}", 
                    experiment.getId(), stock.getId(), stock.getSymbol());

            final StockInfoResponse stockInfo;
            try {
                stockInfo = securityService.getSecurityStockInfoKorea(
                    stock.getId(),
                    stock.getSymbolName(),
                    stock.getSecurityName(),
                    stock.getSymbol(),
                    stock.getExchangeNum(),
                    getCountryFromExchangeNum(stock.getExchangeNum())
                ).block();
            } catch (Exception e) {
                log.error("Failed to fetch stock info for auto-sell - experimentId: {}, stockId: {}, symbol: {}, error: {}", 
                        experiment.getId(), stock.getId(), stock.getSymbol(), e.getMessage(), e);
                return false;
            }

            if (stockInfo != null && stockInfo.getPrice() != null) {
                final Double price = stockInfo.getPrice();
                final LocalDateTime sellAt = LocalDateTime.now();
                // ROI 계산: ((현재가 - 매수가) / 매수가) * 100
                final Double roi = ((price - experiment.getBuyPrice()) / experiment.getBuyPrice()) * 100;

                experiment.updateExperiment(price, "COMPLETE", sellAt, roi);
                // 변경사항 저장
                experimentRepository.save(experiment);
                
                // 완료 시점의 trade item 저장
                try {
                    Optional<Score> scoreOptional = scoreRepository.findByStockIdAndDate(stock.getId(), LocalDate.now());
                    if (scoreOptional.isEmpty()) {
                        scoreOptional = scoreRepository.findTopByStockIdOrderByDateDesc(stock.getId());
                    }
                    
                    int score = experiment.getScore(); // 기본값은 실험의 점수
                    if (scoreOptional.isPresent()) {
                        final Score scoreData = scoreOptional.get();
                        if (stockInfo.getCountry().equals(COUNTRY.KOREA)) {
                            score = scoreData.getScoreKorea();
                        } else {
                            score = scoreData.getScoreOversea();
                        }
                    }
                    
                    final ExperimentTradeItem finalTradeItem = ExperimentTradeItem.builder()
                        .experiment(experiment)
                        .price(price)
                        .roi(roi)
                        .score(score)
                        .tradeAt(sellAt)
                        .build();
                    
                    experimentTradeItemRepository.save(finalTradeItem);
                    log.info("Saved final trade item for completed experiment - experimentId: {}", experiment.getId());
                } catch (Exception e) {
                    log.warn("Failed to save final trade item for experiment - experimentId: {}", experiment.getId(), e);
                    // trade item 저장 실패해도 실험 완료는 성공으로 처리
                }
                
                log.info("Auto-sell completed successfully - experimentId: {}, price: {}, roi: {}", 
                        experiment.getId(), price, roi);
                return true;
            } else {
                log.warn("Failed to get stock info for auto-sell - experimentId: {}, stockId: {}", 
                        experiment.getId(), stock.getId());
                return false;
            }

        } catch (Exception e) {
            log.error("Failed to auto-sell experiment - experimentId: {}", experiment.getId(), e);
            return false;
        }
    }

    // 실험 진행이 5영업일이 지나지 않은 실험 데이터 조회
    public List<Experiment> findExperimentsPrevious5BusinessDays() {
        final LocalDate now = LocalDate.now();
        LocalDate fiveBusinessDaysAgo = calculatePreviousBusinessDate(now);
        LocalDateTime start = fiveBusinessDaysAgo.atTime(LocalTime.MAX);

        final List<Experiment> ExperimentsAfter5BusinessDays = experimentRepository.findProgressExperiments(start, "PROGRESS");

        if (ExperimentsAfter5BusinessDays.isEmpty()) {
            return new ArrayList<>();
        }

        return ExperimentsAfter5BusinessDays;
    }

    public void saveExperiment(Experiment experiment) {

    }

    @Transactional
    public void saveExperimentTradeItem(Experiment experiment) {
        final LocalDateTime now = LocalDateTime.now();
        final LocalDateTime startOfToday = now.toLocalDate().atStartOfDay(); // 오늘 시작 시간
        final LocalDateTime endOfToday = now.toLocalDate().atTime(LocalTime.MAX); // 오늘 마지막 시간

        final List<ExperimentTradeItem> experimentTradeItemsByExperimentId = experimentTradeItemRepository.findExperimentTradeItemsForToday(experiment.getId(), startOfToday, endOfToday);

        if (experimentTradeItemsByExperimentId.isEmpty()) {
            final Stock stock = experiment.getStock();

            StockInfoResponse stockInfoResponse;
            try {
                stockInfoResponse = securityService.getSecurityStockInfoKorea(
                    stock.getId(),
                    stock.getSymbolName(),
                    stock.getSecurityName(),
                    stock.getSymbol(),
                    stock.getExchangeNum(),
                    getCountryFromExchangeNum(stock.getExchangeNum())
                ).block();
            } catch (Exception e) {
                log.error("Failed to get StockInfo for saveExperimentTradeItem - experimentId: {}, stockId: {}", 
                    experiment.getId(), stock.getId(), e);
                return;
            }

            if (stockInfoResponse == null || stockInfoResponse.getPrice() == null) {
                log.warn("StockInfo or price is null for saveExperimentTradeItem - experimentId: {}, stockId: {}", 
                    experiment.getId(), stock.getId());
                return;
            }

            final Double price = stockInfoResponse.getPrice();
            double roi = ((price - experiment.getBuyPrice()) / experiment.getBuyPrice()) * 100;
            
            Optional<Score> scoreOptional = scoreRepository.findByStockIdAndDate(experiment.getStock().getId(), LocalDate.now());
            if (scoreOptional.isEmpty()) {
                log.warn("Today's score not found for saveExperimentTradeItem - experimentId: {}, stockId: {}", 
                    experiment.getId(), stock.getId());
                return;
            }
            
            final Score findByStockIdAndDate = scoreOptional.get();
            int score = 9999;

            if (stockInfoResponse.getCountry().equals(COUNTRY.KOREA)) {
                score = findByStockIdAndDate.getScoreKorea();
            } else {
                score = findByStockIdAndDate.getScoreOversea();
            }

            final ExperimentTradeItem experimentTradeItem = ExperimentTradeItem.builder()
                .tradeAt(now)
                .score(score)
                .roi(roi)
                .price(price)
                .experiment(experiment)
                .build();

            experimentTradeItemRepository.save(experimentTradeItem);
            
            // Experiment 엔티티의 ROI도 업데이트 (PROGRESS 상태일 때만)
            if ("PROGRESS".equals(experiment.getStatus())) {
                experiment.updateExperiment(null, "PROGRESS", null, roi);
                experimentRepository.save(experiment);
            }
            
            log.debug("Saved ExperimentTradeItem for experimentId: {}, updated ROI: {}", experiment.getId(), roi);
        }
    }
}

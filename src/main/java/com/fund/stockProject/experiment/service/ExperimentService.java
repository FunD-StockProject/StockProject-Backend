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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
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

    /*
     * 실험실 - 매수 현황
     * */
    public Mono<ExperimentStatusResponse> getExperimentStatus(final CustomUserDetails customUserDetails) {
        // 로그인한 유저 관련 모의 투자 정보 조회
final List<Experiment> experimentsByUserId = experimentRepository.findExperimentsByEmail(
    customUserDetails.getEmail());

        if (experimentsByUserId.isEmpty()) {
            // 빈 실험일 때도 기본값을 가진 응답 반환 (에러가 아닌 빈 상태)
            return Mono.just(ExperimentStatusResponse.builder()
                .progressExperiments(new ArrayList<>())
                .completeExperiments(new ArrayList<>())
                .avgRoi(0.0)
                .totalTradeCount(0)
                .progressTradeCount(0)
                .successRate(0.0)
                .build());
        }

        // 진행중인 모의 투자 종목
        final List<ExperimentInfoResponse> progressExperimentsInfo = new ArrayList<>();
        // 완료된 모의 투자 종목
        final List<ExperimentInfoResponse> completeExperimentsInfo = new ArrayList<>();

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
            StockInfoResponse stockInfoKorea;
            try {
                stockInfoKorea = securityService.getSecurityStockInfoKorea(
                    stock.getId(),
                    stock.getSymbolName(),
                    stock.getSecurityName(),
                    stock.getSymbol(),
                    stock.getExchangeNum(),
                    getCountryFromExchangeNum(stock.getExchangeNum())
                ).block();
                
                if (stockInfoKorea == null) {
                    log.warn("StockInfo is null for stockId: {}, experimentId: {}", 
                        stock.getId(), experiment.getId());
                    continue; // 해당 실험을 건너뛰고 다음 실험 처리
                }
            } catch (Exception e) {
                log.error("Failed to get StockInfo for stockId: {}, experimentId: {}", 
                    stock.getId(), experiment.getId(), e);
                continue; // 해당 실험을 건너뛰고 다음 실험 처리
            }

            if (experiment.getStatus().equals("PROGRESS")) {
                progressExperimentsInfo.add(ExperimentInfoResponse.builder()
                    .experimentId(experiment.getId())
                    .roi(experiment.getRoi())
                    .buyAt(experiment.getBuyAt())
                    .buyPrice(experiment.getBuyPrice().intValue())
                    .symbolName(stock.getSymbolName())
                    .status(experiment.getStatus())
                    .country(stockInfoKorea.getCountry())
                    .build());

                continue;
            }

            completeExperimentsInfo.add(ExperimentInfoResponse.builder()
                .experimentId(experiment.getId())
                .roi(experiment.getRoi())
                .buyAt(experiment.getBuyAt())
                .buyPrice(experiment.getBuyPrice().intValue())
                .symbolName(stock.getSymbolName())
                .status(experiment.getStatus())
                .country(stockInfoKorea.getCountry())
                .build());
        }

        final int countByStatusCompleted = experimentRepository.countExperimentsByStatus("PROGRESS"); // 진행중인 실험 수

        final double averageRoi = experimentsByUserId.stream()
            .mapToDouble(Experiment::getRoi) // 각 ROI 값을 double로 추출
            .average()                            // OptionalDouble 반환
            .orElse(0.0);

        final long count = experimentsByUserId.stream().filter(p -> p.getSellPrice() - p.getBuyPrice() > 0).count(); // 모의투자에 성공한 종목 개수
        double successRate = ((double) count / experimentsByUserId.size()) * 100;

        final ExperimentStatusResponse experimentStatusResponse = ExperimentStatusResponse.builder()
            .progressExperiments(progressExperimentsInfo) // 진행중인 실험 정보
            .completeExperiments(completeExperimentsInfo) // 완료된 실험 정보
            .avgRoi(averageRoi) // 평균 수익률
            .totalTradeCount(experimentsByUserId.size()) // 총 실험 수 (전체 모의투자 개수)
            .progressTradeCount(countByStatusCompleted) // 진행중인 실험 수 (진행중인 모의투자 개수)
            .successRate(successRate) // 성공률
            .build();

        return Mono.just(experimentStatusResponse);
    }

    /*
     * 실험실 - 종목 매수 현황 자세히 보기
     * */
    public Mono<ExperimentStatusDetailResponse> getExperimentStatusDetail(final Integer experimentId) {
        // 자세히 보기 선택한 실험 데이터 조회
        final Optional<Experiment> experimentOptional = experimentRepository.findExperimentByExperimentId(experimentId);
        if (experimentOptional.isEmpty()) {
            log.warn("Experiment not found - experimentId: {}", experimentId);
            return Mono.error(new NoSuchElementException("실험을 찾을 수 없습니다"));
        }
        final Experiment experiment = experimentOptional.get();
        
        // 실험 데이터에 해당하는 자동 모의 실험 내역 조회
        final List<ExperimentTradeItem> experimentTradeItems = experimentTradeItemRepository.findExperimentTradeItemsByExperimentId(experimentId);
        
        // experimentTradeItems가 비어있을 수 있음
        if (experimentTradeItems.isEmpty()) {
            log.warn("No trade items found for experimentId: {}", experimentId);
            return Mono.just(ExperimentStatusDetailResponse.builder()
                .tradeInfos(new ArrayList<>())
                .roi(0.0)
                .status(experiment.getStatus())
                .symbolName(experiment.getStock().getSymbolName())
                .build());
        }
        
        // 가장 최근 수익률 조회
        final ExperimentTradeItem recentExperimentTradeItem = experimentTradeItems.get(experimentTradeItems.size() - 1);

        // 최종 수익률 계산:  ((판매가 - 매수가) / 매수가) * 100
        double roi = ((recentExperimentTradeItem.getPrice() - experiment.getBuyPrice()) / experiment.getBuyPrice()) * 100;

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

        return Mono.just(ExperimentStatusDetailResponse.builder()
            .tradeInfos(tradeInfos)
            .roi(roi)
            .status(experiment.getStatus())
            .symbolName(experiment.getStock().getSymbolName())
            .build());
    }

    private COUNTRY getCountryFromExchangeNum(EXCHANGENUM exchangenum) {
        return List.of(EXCHANGENUM.KOSPI, EXCHANGENUM.KOSDAQ, EXCHANGENUM.KOREAN_ETF).contains(exchangenum) ? COUNTRY.KOREA : COUNTRY.OVERSEA;
    }

    public Mono<ExperimentSimpleResponse> buyExperiment(final CustomUserDetails customUserDetails, final Integer stockId, String country) {
        // Stock 조회 및 검증
        final Optional<Stock> stockById = stockRepository.findStockById(stockId);
        if (stockById.isEmpty()) {
            log.warn("Stock not found - stockId: {}", stockId);
            return Mono.just(ExperimentSimpleResponse.builder()
                .message("종목을 찾을 수 없습니다")
                .success(false)
                .price(0.0d)
                .build()
            );
        }
        final Stock stock = stockById.get();

        // User 조회 및 검증
        final Optional<User> userById = userRepository.findByEmail(customUserDetails.getEmail());
        if (userById.isEmpty()) {
            log.warn("User not found - email: {}", customUserDetails.getEmail());
            return Mono.just(ExperimentSimpleResponse.builder()
                .message("사용자 정보를 찾을 수 없습니다")
                .success(false)
                .price(0.0d)
                .build()
            );
        }
        final User user = userById.get();

        final LocalDateTime now = LocalDateTime.now(); // 현재 날짜와 시간
        final LocalTime current = now.toLocalTime(); // 현재 시간
        final LocalDateTime startOfToday = now.toLocalDate().atStartOfDay(); // 오늘 시작 시간
        final LocalDateTime endOfToday = now.toLocalDate().atTime(LocalTime.MAX); // 오늘 마지막 시간
        final DayOfWeek dayOfWeek = now.getDayOfWeek(); // 요일
        Double price = 0.0d;

        // Score 조회 및 검증 - 오늘 날짜 우선, 없으면 최신 점수 사용
        Optional<Score> scoreOptional = scoreRepository.findByStockIdAndDate(stockId, LocalDate.now());
        if (scoreOptional.isEmpty()) {
            log.warn("Today's score not found for stock - stockId: {}, trying latest score", stockId);
            // 오늘 날짜 점수가 없으면 최신 점수 조회 시도
            scoreOptional = scoreRepository.findTopByStockIdOrderByDateDesc(stockId);
            if (scoreOptional.isEmpty()) {
                log.error("No score found for stock - stockId: {}", stockId);
                return Mono.just(ExperimentSimpleResponse.builder()
                    .message("점수 정보를 찾을 수 없습니다")
                    .success(false)
                    .price(0.0d)
                    .build()
                );
            }
            log.info("Using latest score instead of today's score for stockId: {}", stockId);
        }
        final Score findByStockIdAndDate = scoreOptional.get();
        int score = 9999;

        // 차트에서 이미 작동하는 getSecurityStockInfoKorea 사용 (inquire-price API)
        // inquire-price-2는 작동하지 않으므로 inquire-price로 변경
        final Mono<StockInfoResponse> securityStockInfoKorea = securityService.getSecurityStockInfoKorea(
            stock.getId(),
            stock.getSymbolName(),
            stock.getSecurityName(),
            stock.getSymbol(),
            stock.getExchangeNum(),
            getCountryFromExchangeNum(stock.getExchangeNum())
        );

        // StockInfo 조회 및 검증 - 에러 시 null 반환하도록 처리
        StockInfoResponse stockInfoResponse;
        try {
            stockInfoResponse = securityStockInfoKorea
                .onErrorResume(error -> {
                    log.warn("Failed to get StockInfo for stock - stockId: {}, error: {}", stockId, error.getMessage());
                    return Mono.empty();  // 에러 발생 시 빈 Mono 반환
                })
                .blockOptional()
                .orElse(null);  // Optional이 비어있으면 null 반환
        } catch (Exception e) {
            log.warn("Failed to get StockInfo for stock - stockId: {}, error: {}", stockId, e.getMessage());
            return Mono.just(ExperimentSimpleResponse.builder()
                .message("주가 정보를 가져올 수 없습니다")
                .success(false)
                .price(0.0d)
                .build()
            );
        }
        
        if (stockInfoResponse == null) {
            log.warn("StockInfo is null for stock - stockId: {}", stockId);
            return Mono.just(ExperimentSimpleResponse.builder()
                .message("주가 정보를 가져올 수 없습니다")
                .success(false)
                .price(0.0d)
                .build()
            );
        }

        if (stockInfoResponse.getCountry().equals(COUNTRY.KOREA)) {
            score = findByStockIdAndDate.getScoreKorea();
            final Optional<Experiment> experimentByStockIdAndBuyAt = experimentRepository.findExperimentByStockIdForToday(stockId, startOfToday, endOfToday);

            // 하루에 같은 종목 중복 구매 불가 처리
            if (experimentByStockIdAndBuyAt.isPresent()) {
                return Mono.just(ExperimentSimpleResponse.builder()
                    .message("같은 종목 중복 구매")
                    .success(false)
                    .price(0.0d)
                    .build()
                );
            }

            LocalTime koreaEndTime = LocalTime.of(17, 0);

            // 종가 결정
            if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                price = stockInfoResponse.getYesterdayPrice();
            } else {
                price = current.isBefore(koreaEndTime) ? stockInfoResponse.getYesterdayPrice() : stockInfoResponse.getPrice();
            }
        } else {
            // 해외 주식 로직
            score = findByStockIdAndDate.getScoreOversea();

            // 해당 구간에 이미 매수한 경우 중복 매수 방지
            Optional<Experiment> existingItem = experimentRepository.findExperimentByStockIdForToday(stockId, startOfToday, endOfToday);

            if (existingItem.isPresent()) {
                return Mono.just(ExperimentSimpleResponse.builder()
                    .message("같은 종목 중복 구매")
                    .success(false)
                    .price(0.0d)
                    .build()
                );
            }

            LocalTime overseasEndTime = LocalTime.of(6, 0);
            // 종가 결정
            price = current.isBefore(overseasEndTime) ? stockInfoResponse.getYesterdayPrice() : stockInfoResponse.getPrice();

            // 종가 결정
            if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                price = stockInfoResponse.getYesterdayPrice();
            } else {
                price = current.isBefore(overseasEndTime) ? stockInfoResponse.getYesterdayPrice() : stockInfoResponse.getPrice();
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

        return Mono.just(ExperimentSimpleResponse.builder()
            .message("모의 매수 성공")
            .success(true)
            .price(price)
            .build()
        );
    }

    // 매수결과 조회
    public Mono<ExperimentReportResponse> getReport(CustomUserDetails customUserDetails) {
        final String email = customUserDetails.getEmail();

        // 인간지표 점수대별 평균 수익률
        final List<ReportStatisticDto> reportStatisticDtos = new ArrayList<>();

        // 1. 60점 이하 평균 수익률
        final double totalAvgRoi_0_59 = experimentRepository.findTotalAvgRoi(0, 59);
        final double userAvgRoi_0_59 = experimentRepository.findUserAvgRoi(0, 59, email);

        reportStatisticDtos.add(ReportStatisticDto.builder()
            .totalAvgRoi(totalAvgRoi_0_59)
            .userAvgRoi(userAvgRoi_0_59)
            .scoreRange(SCORERANGE.RANGE_0_59.getRange())
            .build());

        // 2. 60~69점 평균 수익률
        final double totalAvgRoi_60_69 = experimentRepository.findTotalAvgRoi(60, 69);
        final double userAvgRoi_60_69 = experimentRepository.findUserAvgRoi(60, 69, email);

        reportStatisticDtos.add(ReportStatisticDto.builder()
            .totalAvgRoi(totalAvgRoi_60_69)
            .userAvgRoi(userAvgRoi_60_69)
            .scoreRange(SCORERANGE.RANGE_60_69.getRange())
            .build());

        // 3. 70~79점 평균 수익률
        final double totalAvgRoi_70_79 = experimentRepository.findTotalAvgRoi(70, 79);
        final double userAvgRoi_70_79 = experimentRepository.findUserAvgRoi(70, 79, email);

        reportStatisticDtos.add(ReportStatisticDto.builder()
            .totalAvgRoi(totalAvgRoi_70_79)
            .userAvgRoi(userAvgRoi_70_79)
            .scoreRange(SCORERANGE.RANGE_70_79.getRange())
            .build());

        // 3. 80~89점 평균 수익률
        final double totalAvgRoi_80_89 = experimentRepository.findTotalAvgRoi(80, 89);
        final double userAvgRoi_80_89 = experimentRepository.findUserAvgRoi(80, 89, email);

        reportStatisticDtos.add(ReportStatisticDto.builder()
            .totalAvgRoi(totalAvgRoi_80_89)
            .userAvgRoi(userAvgRoi_80_89)
            .scoreRange(SCORERANGE.RANGE_80_89.getRange())
            .build());

        // 5. 90이상 평균 수익률
        final double totalAvgRoi_90_100 = experimentRepository.findTotalAvgRoi(90, 100);
        final double userAvgRoi_90_100 = experimentRepository.findUserAvgRoi(90, 100, email);

        reportStatisticDtos.add(ReportStatisticDto.builder()
            .totalAvgRoi(totalAvgRoi_90_100)
            .userAvgRoi(userAvgRoi_90_100)
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
            email, "COMPLETED");

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
        final long userCount = userRepository.count();
        long sameGradeUserRage = (countSameGradeUser * 100L / userCount);

        final List<ReportPatternDto> reportPatternDtos = new ArrayList<>();

        // 인간지표 점수 별 투자 유형 패턴 데이터
        final List<Object[]> experimentGroupByBuyAt = experimentRepository.findExperimentGroupByBuyAt();

        for (final Object[] row : experimentGroupByBuyAt) {
            // Object[]에서 데이터 추출: [buy_date, avg_roi, avg_score]
            LocalDate buyDate = ((java.sql.Date) row[0]).toLocalDate();
            Double avgRoi = (Double) row[1];
            Double avgScore = (Double) row[2];
            
            reportPatternDtos.add(
                ReportPatternDto.builder()
                    .score(avgScore.intValue())
                    .buyAt(buyDate.atStartOfDay())
                    .roi(avgRoi)
                    .build()
            );
        }

        // 투자 패턴 그래프 데이터
        final ExperimentReportResponse experimentReportResponse = ExperimentReportResponse.builder()
            .weeklyExperimentCount(weeklyExperimentCount)
            .reportStatisticDtos(reportStatisticDtos)
            .sameGradeUserRate(sameGradeUserRage)
            .successUserExperiments(successExperimentCount)
            .totalUserExperiments(totalExperimentCount)
            .reportPatternDtos(reportPatternDtos)
            .build();

        return Mono.just(experimentReportResponse);
    }

    // Lab 결과 화면용 응답 (mock 구조 기반)
    public Mono<PortfolioResultResponse> getPortfolioResult(final CustomUserDetails customUserDetails) {
        final String email = customUserDetails.getEmail();

        // 완료/진행 실험 분리 집계
        final List<Experiment> completed = experimentRepository.findExperimentsByEmailAndStatus(email, "COMPLETED");
        final List<Experiment> progress = experimentRepository.findExperimentsByEmailAndStatus(email, "PROGRESS");

        long totalCompleted = completed.size();
        long totalProgress = progress.size();
        long purchasedCountAll = totalCompleted + totalProgress; // 전체 진행(완료+진행)
        long profitCount = completed.stream().filter(e -> e.getRoi() != null && e.getRoi() > 0).count();

        // 성공률(%) 및 구간 라벨 생성
        double successRateVal = totalCompleted == 0 ? 0.0 : (profitCount * 100.0 / totalCompleted);
        String successRateLabel;
        if (successRateVal <= 20) successRateLabel = "0~20%";
        else if (successRateVal <= 40) successRateLabel = "21~40%";
        else if (successRateVal <= 60) successRateLabel = "41~60%";
        else if (successRateVal <= 80) successRateLabel = "61~80%";
        else successRateLabel = "81~100%";

        // 최고/최저 수익률 기준 점수 및 구간명 산출
        PortfolioResultResponse.ProfitBound highest = null;
        PortfolioResultResponse.ProfitBound lowest = null;
        if (!completed.isEmpty()) {
            Experiment max = completed.stream().max((a, b) -> Double.compare(a.getRoi(), b.getRoi())).get();
            Experiment min = completed.stream().min((a, b) -> Double.compare(a.getRoi(), b.getRoi())).get();
            highest = PortfolioResultResponse.ProfitBound.builder()
                .score(max.getScore())
                .range(toScoreRangeLabel(max.getScore()))
                .build();
            lowest = PortfolioResultResponse.ProfitBound.builder()
                .score(min.getScore())
                .range(toScoreRangeLabel(min.getScore()))
                .build();
        }

        // 점수 구간별 사용자 평균 수익률 → scoreTable (avg)
        double u_0_59 = experimentRepository.findUserAvgRoi(0, 59, email);
        double u_60_69 = experimentRepository.findUserAvgRoi(60, 69, email);
        double u_70_79 = experimentRepository.findUserAvgRoi(70, 79, email);
        double u_80_100 = experimentRepository.findUserAvgRoi(80, 100, email);

        // median 계산: 완료된 실험에서 구간별 ROI 중앙값
        java.util.function.Function<int[], Double> medianByRange = (range) -> {
            int start = range[0], end = range[1];
            List<Double> list = completed.stream()
                .filter(e -> e.getRoi() != null)
                .filter(e -> e.getScore() >= start && e.getScore() <= end)
                .map(Experiment::getRoi)
                .sorted()
                .toList();
            if (list.isEmpty()) return null;
            int n = list.size();
            if (n % 2 == 1) return list.get(n / 2);
            return (list.get(n / 2 - 1) + list.get(n / 2)) / 2.0;
        };

        List<PortfolioResultResponse.ScoreTableItem> scoreTable = new ArrayList<>();
        scoreTable.add(PortfolioResultResponse.ScoreTableItem.builder().range("60점 이하").avg(u_0_59).median(medianByRange.apply(new int[]{0,59})).build());
        scoreTable.add(PortfolioResultResponse.ScoreTableItem.builder().range("60-70점").avg(u_60_69).median(medianByRange.apply(new int[]{60,69})).build());
        scoreTable.add(PortfolioResultResponse.ScoreTableItem.builder().range("70-80점").avg(u_70_79).median(medianByRange.apply(new int[]{70,79})).build());
        scoreTable.add(PortfolioResultResponse.ScoreTableItem.builder().range("80점 이상").avg(u_80_100).median(medianByRange.apply(new int[]{80,100})).build());

        // 패턴 그래프용 히스토리 포인트 (score, roi, buyAt → x,y,label)
        final List<Object[]> experimentGroupByBuyAt = experimentRepository.findExperimentGroupByBuyAt();
        List<PortfolioResultResponse.HistoryPoint> history = experimentGroupByBuyAt.stream().map(row -> {
                java.time.LocalDate buyDate = ((java.sql.Date) row[0]).toLocalDate();
                Double avgRoi = (Double) row[1];
                Double avgScore = (Double) row[2];
                return PortfolioResultResponse.HistoryPoint.builder()
                    .x(avgScore.intValue())
                    .y(avgRoi)
                    .label(PortfolioResultResponse.HistoryPoint.toLabel(buyDate.atStartOfDay()))
                    .build();
            })
            .collect(java.util.stream.Collectors.toList());

        // HumanIndex 계산: userScore(평균 점수 반올림), userType(성공률 기반 라벨), maintainRate(진행/전체)
        Integer userScore = completed.isEmpty() ? null : (int) Math.round(completed.stream()
            .mapToInt(Experiment::getScore)
            .average().orElse(Double.NaN));

        String userType;
        if (successRateVal <= 20) userType = "초심자형";
        else if (successRateVal <= 40) userType = "보수형";
        else if (successRateVal <= 60) userType = "균형형";
        else if (successRateVal <= 80) userType = "공격형";
        else userType = "고수형";

        String maintainRate = purchasedCountAll == 0 ? "0%" : Math.round(totalProgress * 100.0 / purchasedCountAll) + "%";

        PortfolioResultResponse.HumanIndex humanIndex = PortfolioResultResponse.HumanIndex.builder()
            .userScore(userScore)
            .userType(userType)
            .successRate(successRateLabel)
            .maintainRate(maintainRate)
            .purchasedCount(purchasedCountAll)
            .profitCount(profitCount)
            .build();

        // InvestmentPattern: 사용자 평균 수익률이 가장 높은 구간 레이블 기반으로 간단 추론
        double maxAvg = Math.max(Math.max(u_0_59, u_60_69), Math.max(u_70_79, u_80_100));
        String patternType;
        String patternDesc;
        if (maxAvg == u_0_59) { patternType = "역발상형"; patternDesc = "점수가 낮을 때 진입해 반등을 노리는 경향"; }
        else if (maxAvg == u_60_69) { patternType = "보수 추세형"; patternDesc = "중간 점수대에서 안정적 추세를 선호"; }
        else if (maxAvg == u_70_79) { patternType = "가치 선점형"; patternDesc = "높아지기 전 구간에서 선제 진입을 선호"; }
        else { patternType = "추세 추종형"; patternDesc = "높은 점수대의 강한 추세를 추종"; }

        PortfolioResultResponse.InvestmentPattern investmentPattern = PortfolioResultResponse.InvestmentPattern.builder()
            .patternType(patternType)
            .patternDescription(patternDesc)
            .build();

        PortfolioResultResponse.ExperimentSummary summary = PortfolioResultResponse.ExperimentSummary.builder()
            .totalExperiments(purchasedCountAll)
            .highestProfit(highest)
            .lowestProfit(lowest)
            .build();

        PortfolioResultResponse response = PortfolioResultResponse.builder()
            .scoreTable(scoreTable)
            .experimentSummary(summary)
            .humanIndex(humanIndex)
            .investmentPattern(investmentPattern)
            .history(history)
            .build();

        return Mono.just(response);
    }

    private String toScoreRangeLabel(int score) {
        if (score <= 59) return "60점 이하";
        if (score <= 69) return "60-70점";
        if (score <= 79) return "70-80점";
        return "80점 이상";
    }

    // 영업일 기준 실험 진행한 기간이 5일째인 실험 데이터 조회
    @Transactional(readOnly = true)
    public List<Experiment> findExperimentsAfter5BusinessDays() {
        LocalDate fiveBusinessDaysAgo = calculatePreviousBusinessDate(LocalDate.now());
        LocalDateTime start = fiveBusinessDaysAgo.atStartOfDay();
        LocalDateTime end = fiveBusinessDaysAgo.atTime(LocalTime.MAX);

        final List<Experiment> ExperimentsAfter5BusinessDays = experimentRepository.findExperimentsAfterFiveDays(start, end);

        if (ExperimentsAfter5BusinessDays.isEmpty()) {
            return new ArrayList<>();
        }

        return ExperimentsAfter5BusinessDays;
    }

    // 5영업일 전 날짜 찾는 함수
    private LocalDate calculatePreviousBusinessDate(LocalDate fromDate) {
        int daysCounted = 0;
        LocalDate date = fromDate;

        while (daysCounted < 5) {
            date = date.minusDays(1);
            DayOfWeek day = date.getDayOfWeek();

            // 주말 제외
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                daysCounted++;
            }
        }

        return date;
    }

    // 자동판매 - 실험 데이터 수정
    public void updateExperiment(Experiment experiment) {
        try {
            log.info("Starting auto-sell update for experiment - experimentId: {}, stockId: {}, symbol: {}", 
                    experiment.getId(), experiment.getStock().getId(), experiment.getStock().getSymbol());
            final Stock stock = experiment.getStock();

            final Mono<StockInfoResponse> securityStockInfoKorea = securityService.getSecurityStockInfoKorea
                (
                    stock.getId(),
                    stock.getSymbolName(),
                    stock.getSecurityName(),
                    stock.getSymbol(),
                    stock.getExchangeNum(),
                    getCountryFromExchangeNum(stock.getExchangeNum())
                );

            if (securityStockInfoKorea.blockOptional().isPresent()) {
                final Double price = securityStockInfoKorea.block().getPrice();
                final Double roi = ((experiment.getBuyPrice() - price) % experiment.getBuyPrice()) * 100;

                experiment.updateExperiment(price, "COMPLETE", LocalDateTime.now(), roi);
                log.info("Auto-sell completed successfully - experimentId: {}, price: {}, roi: {}", 
                        experiment.getId(), price, roi);
            } else {
                log.warn("Failed to get stock info for auto-sell - experimentId: {}, stockId: {}", 
                        experiment.getId(), stock.getId());
            }

        } catch (Exception e) {
            log.error("Failed to auto-sell experiment - experimentId: {}", experiment.getId(), e);
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

    public void saveExperimentTradeItem(Experiment experiment) {
        final LocalDateTime now = LocalDateTime.now();
        final LocalDateTime startOfToday = now.toLocalDate().atStartOfDay(); // 오늘 시작 시간
        final LocalDateTime endOfToday = now.toLocalDate().atTime(LocalTime.MAX); // 오늘 마지막 시간

        final List<ExperimentTradeItem> experimentTradeItemsByExperimentId = experimentTradeItemRepository.findExperimentTradeItemsForToday(experiment.getId(), startOfToday, endOfToday);

        if (experimentTradeItemsByExperimentId.isEmpty()) {
            final Stock stock = experiment.getStock();

            final Mono<StockInfoResponse> securityStockInfoKorea = securityService.getSecurityStockInfoKorea
                (
                    stock.getId(),
                    stock.getSymbolName(),
                    stock.getSecurityName(),
                    stock.getSymbol(),
                    stock.getExchangeNum(),
                    getCountryFromExchangeNum(stock.getExchangeNum())
                );

            final StockInfoResponse stockInfoResponse = securityStockInfoKorea.block();

            final Double price = stockInfoResponse.getPrice();
            double roi = ((price - experiment.getBuyPrice()) / experiment.getBuyPrice()) * 100;
            final Score findByStockIdAndDate = scoreRepository.findByStockIdAndDate(experiment.getStock().getId(), LocalDate.now()).get();
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
        }
    }
}

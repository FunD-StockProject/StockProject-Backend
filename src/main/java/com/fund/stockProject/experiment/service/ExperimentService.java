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
    private final ExperimentTradeItemRepository experimentTradeItemRepository;

    /*
     * 실험실 - 매수 현황
     * */
    public Mono<ExperimentStatusResponse> getExperimentStatus(final CustomUserDetails customUserDetails) {
        // 로그인한 유저 관련 모의 투자 정보 조회
final List<Experiment> experimentsByUserId = experimentRepository.findExperimentsByEmail(
    customUserDetails.getEmail());

        if (experimentsByUserId.isEmpty()) {
            return Mono.empty();
        }

        // 진행중인 모의 투자 종목
        final List<ExperimentInfoResponse> progressExperimentsInfo = new ArrayList<>();
        // 완료된 모의 투자 종목
        final List<ExperimentInfoResponse> completeExperimentsInfo = new ArrayList<>();

        // 로그인한 유저 관련 모의 투자 정보 조회 진행/완료 리스트에 저장
        for (final Experiment experiment : experimentsByUserId) {
            final Optional<Stock> bySymbol = stockRepository.findBySymbol(experiment.getStock().getSymbol());

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

            if (experiment.getStatus().equals("PROGRESS")) {
                progressExperimentsInfo.add(ExperimentInfoResponse.builder()
                    .experimentId(experiment.getId())
                    .roi(experiment.getRoi())
                    .buyAt(experiment.getBuyAt())
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
        final Experiment experiment = experimentRepository.findExperimentByExperimentId(experimentId).get();
        // 실험 데이터에 해당하는 자동 모의 실험 내역 조회
        final List<ExperimentTradeItem> experimentTradeItems = experimentTradeItemRepository.findExperimentTradeItemsByExperimentId(experimentId);
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
        final Optional<Stock> stockById = stockRepository.findStockById(stockId);
        final Optional<User> userById = userRepository.findByEmail(customUserDetails.getEmail());

        final Stock stock = stockById.get();
        final User user = userById.get();

        final LocalDateTime now = LocalDateTime.now(); // 현재 날짜와 시간
        final LocalTime current = now.toLocalTime(); // 현재 시간
        final LocalDateTime startOfToday = now.toLocalDate().atStartOfDay(); // 오늘 시작 시간
        final LocalDateTime endOfToday = now.toLocalDate().atTime(LocalTime.MAX); // 오늘 마지막 시간
        final DayOfWeek dayOfWeek = now.getDayOfWeek(); // 요일
        Double price = 0.0d;

        final Score findByStockIdAndDate = scoreRepository.findByStockIdAndDate(stockId, LocalDate.now()).get();
        int score = 9999;

        final Mono<StockInfoResponse> securityStockInfoKorea = securityService.getSecurityStockInfoKorea2(
            stock.getId(),
            stock.getSymbolName(),
            stock.getSecurityName(),
            stock.getSymbol(),
            stock.getExchangeNum(),
            getCountryFromExchangeNum(stock.getExchangeNum())
        );

        if (securityStockInfoKorea.blockOptional().isEmpty()) {
            return Mono.empty();
        }

        final StockInfoResponse stockInfoResponse = securityStockInfoKorea.block();

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
            }

        } catch (Exception e) {
            System.err.println("Failed to autoSell");
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

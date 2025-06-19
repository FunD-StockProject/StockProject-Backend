package com.fund.stockProject.preference.service;

import com.fund.stockProject.auth.entity.User;
import com.fund.stockProject.auth.repository.UserRepository;
import com.fund.stockProject.auth.service.AuthService;
import com.fund.stockProject.preference.domain.PreferenceType;
import com.fund.stockProject.preference.dto.BookmarkInfoResponse;
import com.fund.stockProject.preference.entity.Preference;
import com.fund.stockProject.preference.repository.PreferenceRepository;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.domain.EXCHANGENUM;
import com.fund.stockProject.stock.dto.response.StockInfoResponse;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.repository.StockRepository;
import com.fund.stockProject.stock.service.SecurityService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PreferenceService {
    private final PreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final SecurityService securityService;

    public void addBookmark(Integer stockId) {
        setPreference(stockId, PreferenceType.BOOKMARK);
    }

    public void removeBookmark(Integer stockId) {
        removePreference(stockId, PreferenceType.BOOKMARK);
    }

    public void hideStock(Integer stockId) {
        setPreference(stockId, PreferenceType.NEVER_SHOW);
    }

    public void showStock(Integer stockId) {
        removePreference(stockId, PreferenceType.NEVER_SHOW);
    }

    public List<BookmarkInfoResponse> getBookmarks() {
        Integer currentUserId = AuthService.getCurrentUserId();

        List<Stock> stockList = preferenceRepository.findByUserIdAndPreferenceType(currentUserId, PreferenceType.BOOKMARK)
                .stream()
                .map(Preference::getStock)
                .toList();

        if (stockList.isEmpty()) {
            return List.of();
        }

        return Flux.fromIterable(stockList)
                .parallel()
                .runOn(Schedulers.boundedElastic())
                .flatMap(stock -> {
                    // 1. stock 변수의 스코프가 flatMap 내부에서 유지됩니다.
                    COUNTRY country = getCountryFromExchangeNum(stock.getExchangeNum());

                    // 2. 비동기 API를 호출하고, 그 결과와 기존 stock 객체를 함께 사용합니다.
                    return securityService.getSecurityStockInfoKorea(
                                    stock.getId(), stock.getSymbolName(), stock.getSecurityName(),
                                    stock.getSymbol(), stock.getExchangeNum(), country
                            )
                            .map(stockInfoResponse -> {
                                // 3. Score 안정성을 확보하여 최종 응답 객체(Optional)를 만듭니다.
                                return stock.getScores().stream()
                                        .findFirst()
                                        .map(firstScore -> {
                                            Integer score = (country == COUNTRY.KOREA)
                                                    ? firstScore.getScoreKorea()
                                                    : firstScore.getScoreOversea();

                                            return new BookmarkInfoResponse(
                                                    stock.getSymbolName(),
                                                    stockInfoResponse.getPrice(),
                                                    stockInfoResponse.getPriceDiffPerCent(),
                                                    score,
                                                    firstScore.getDiff()
                                            );
                                        });
                            });
                })
                // flatMap의 결과는 Optional<BookmarkInfoResponse> 이므로, 비어있는 Optional을 걸러냅니다.
                .filter(Optional::isPresent)
                // Optional을 벗겨내어 실제 BookmarkInfoResponse 객체만 남깁니다.
                .map(Optional::get)
                .sequential()
                .collectList()
                .block();
    }

    public Integer getBookmarkCount() {
        Integer currentUserId = AuthService.getCurrentUserId();
        return (int) preferenceRepository.countByUserIdAndPreferenceType(currentUserId, PreferenceType.BOOKMARK);
    }

    private User getUserOrThrow(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Can't find user: " + userId));
    }

    private Stock getStockOrThrow(Integer stockId) {
        return stockRepository.findStockById(stockId)
                .orElseThrow(() -> new EntityNotFoundException("Can't find stock: " + stockId));
    }

    private void setPreference(Integer stockId, PreferenceType newPreferenceType) {
        Integer currentUserId = AuthService.getCurrentUserId();
        Optional<Preference> existingPreference =
                preferenceRepository.findByUserIdAndStockId(currentUserId, stockId);

        if (existingPreference.isPresent()) {
            // 기존 선호도가 있다면 타입만 변경
            existingPreference.get().setPreferenceType(newPreferenceType);
        } else {
            // 기존 선호도가 없다면 User와 Stock을 조회하여 새로 생성
            User user = getUserOrThrow(currentUserId);
            Stock stock = getStockOrThrow(stockId);
            Preference preference = new Preference(user, stock, newPreferenceType);
            preferenceRepository.save(preference);
        }
    }

    private void removePreference(Integer stockId, PreferenceType expectedPreferenceType) {
        Integer currentUserId = AuthService.getCurrentUserId();
        Optional<Preference> existingPreference =
                preferenceRepository.findByUserIdAndStockId(currentUserId, stockId);

        if (existingPreference.isPresent() && existingPreference.get().getPreferenceType() == expectedPreferenceType) {
            preferenceRepository.delete(existingPreference.get());
        } else {
            // 더 구체적인 오류 메시지 제공
            throw new EntityNotFoundException(
                    String.format("지정된 '%s' 타입의 선호도가 존재하지 않거나 일치하지 않습니다: 주식 ID %d", expectedPreferenceType.name(), stockId));
        }
    }

    private COUNTRY getCountryFromExchangeNum(EXCHANGENUM exchangenum) {
        return List.of(EXCHANGENUM.KOSPI, EXCHANGENUM.KOSDAQ, EXCHANGENUM.KOREAN_ETF)
                .contains(exchangenum) ? COUNTRY.KOREA : COUNTRY.OVERSEA;
    }
}

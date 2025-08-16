package com.fund.stockProject.preference.service;

import com.fund.stockProject.user.entity.User;
import com.fund.stockProject.user.repository.UserRepository;
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

import java.util.ArrayList;
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
        Integer currentUserId = getCurrentUserId();

        List<Stock> stockList = preferenceRepository.findByUserIdAndPreferenceType(currentUserId, PreferenceType.BOOKMARK)
                .stream()
                .map(Preference::getStock)
                .toList();

        if (stockList.isEmpty()) {
            return List.of();
        }

        List<BookmarkInfoResponse> result = new ArrayList<>();

        for (Stock stock : stockList) {
            COUNTRY country = getCountryFromExchangeNum(stock.getExchangeNum());

            StockInfoResponse stockInfoResponse = securityService.getSecurityStockInfoKorea(
                    stock.getId(), stock.getSymbolName(), stock.getSecurityName(),
                    stock.getSymbol(), stock.getExchangeNum(), country
            ).block();

            if (stockInfoResponse != null) {
                BookmarkInfoResponse bookmarkInfoResponse = new BookmarkInfoResponse(
                        stock.getSymbolName(),
                        stockInfoResponse.getPrice(),
                        stockInfoResponse.getPriceDiffPerCent(),
                        country == COUNTRY.KOREA ? stock.getScores().get(0).getScoreKorea() : stock.getScores().get(0).getScoreOversea(),
                        stock.getScores().get(0).getDiff()
                );

                result.add(bookmarkInfoResponse);
            }
        }

        return result;
    }


    public Integer getBookmarkCount() {
        Integer currentUserId = getCurrentUserId();
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
        Integer currentUserId = getCurrentUserId();
        Optional<Preference> existingPreference =
                preferenceRepository.findByUserIdAndStockId(currentUserId, stockId);

        if (existingPreference.isPresent()) {
            // 기존 선호도가 있다면 타입만 변경
            existingPreference.get().setPreferenceType(newPreferenceType);
            preferenceRepository.save(existingPreference.get());
        } else {
            // 기존 선호도가 없다면 User와 Stock을 조회하여 새로 생성
            User user = getUserOrThrow(currentUserId);
            Stock stock = getStockOrThrow(stockId);
            Preference preference = new Preference(user, stock, newPreferenceType);
            preferenceRepository.save(preference);
        }
    }

    private void removePreference(Integer stockId, PreferenceType expectedPreferenceType) {
        Integer currentUserId = getCurrentUserId();
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

    private Integer getCurrentUserId() {
        String userEmail = AuthService.getCurrentUserEmail();

        User user = userRepository.findByEmail(userEmail).orElseThrow(
                () -> new EntityNotFoundException("User not found with email: " + userEmail)
        );

        return user.getId();
    }
}

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
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PreferenceService {
    private final PreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final SecurityService securityService;

    @Transactional
    public void addBookmark(Integer stockId) {
        System.out.println("=== 북마크 추가 시작 ===");
        System.out.println("stockId: " + stockId);
        
        try {
            setPreference(stockId, PreferenceType.BOOKMARK);
            System.out.println("Preference 설정 완료");
            
            String email = AuthService.getCurrentUserEmail();
            System.out.println("현재 사용자 이메일: " + email);

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new EntityNotFoundException("User not found with email: " + email));
            System.out.println("사용자 조회 완료: " + user.getNickname());
            
            Stock stock = stockRepository.findStockById(stockId)
                    .orElseThrow(() -> new EntityNotFoundException("Can't find stock: " + stockId));
            System.out.println("주식 조회 완료: " + stock.getSymbolName());

            // 구독 시작 시점에는 알림을 발송하지 않음 (점수 급변 시에만 알림)
            System.out.println("구독 시작 완료 - 점수 급변 시 알림을 드릴게요");
            System.out.println("=== 북마크 추가 완료 ===");
            
        } catch (Exception e) {
            System.out.println("=== 북마크 추가 실패 ===");
            System.out.println("오류 메시지: " + e.getMessage());
            System.out.println("오류 타입: " + e.getClass().getSimpleName());
            e.printStackTrace();
            throw e;
        }
    }

    @Transactional
    public void removeBookmark(Integer stockId) {
        removePreference(stockId, PreferenceType.BOOKMARK);
        String email = AuthService.getCurrentUserEmail();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found with email: " + email));
        Stock stock = stockRepository.findStockById(stockId)
                .orElseThrow(() -> new EntityNotFoundException("Can't find stock: " + stockId));

        // 구독 해제 시점에는 알림을 발송하지 않음
    }

    public void hideStock(Integer stockId) {
        setPreference(stockId, PreferenceType.NEVER_SHOW);
    }

    public void showStock(Integer stockId) {
        removePreference(stockId, PreferenceType.NEVER_SHOW);
    }

    /**
     * 알림 토글 - 북마크가 없으면 북마크+알림 생성, 있으면 알림만 토글
     */
    @Transactional
    public boolean toggleNotification(Integer stockId) {
        String email = AuthService.getCurrentUserEmail();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found with email: " + email));

        // 기존 Preference 찾기
        Optional<Preference> existingPreference = preferenceRepository.findByUserIdAndStockId(user.getId(), stockId);
        
        if (existingPreference.isPresent()) {
            // 북마크가 이미 있는 경우: 알림만 토글
            Preference preference = existingPreference.get();
            if (preference.getPreferenceType() == PreferenceType.BOOKMARK) {
                boolean newNotificationState = !preference.getNotificationEnabled();
                preference.setNotificationEnabled(newNotificationState);
                preferenceRepository.save(preference);
                return newNotificationState;
            } else {
                // 북마크가 아닌 다른 타입(예: NEVER_SHOW)인 경우: 에러 발생
                throw new IllegalStateException("Cannot toggle notification for stock with preference type: " + preference.getPreferenceType());
            }
        } else {
            // 북마크가 없는 경우: 북마크+알림 생성
            Stock stock = getStockOrThrow(stockId);
            Preference newPreference = new Preference(user, stock, PreferenceType.BOOKMARK);
            newPreference.setNotificationEnabled(true);
            preferenceRepository.save(newPreference);
            return true;
        }
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
                // Preference에서 알림 활성화 여부 확인
                Optional<Preference> preference = preferenceRepository.findByUserIdAndStockId(getCurrentUserId(), stock.getId());
                Boolean isNotificationOn = preference.map(Preference::getNotificationEnabled).orElse(true);

                BookmarkInfoResponse bookmarkInfoResponse = BookmarkInfoResponse.builder()
                        .stockId(stock.getId())
                        .name(stock.getSecurityName())
                        .price(stockInfoResponse.getPrice() != null ? stockInfoResponse.getPrice().intValue() : null)
                        .priceDiffPerCent(stockInfoResponse.getPriceDiffPerCent())
                        .score(country == COUNTRY.KOREA ? stock.getScores().get(0).getScoreKorea() : stock.getScores().get(0).getScoreOversea())
                        .diff(stock.getScores().get(0).getDiff())
                        .isNotificationOn(isNotificationOn)
                        .symbolName(stock.getSymbolName())
                        .country(country)
                        .build();

                result.add(bookmarkInfoResponse);
            }
        }

        return result;
    }


    public Integer getBookmarkCount() {
        Integer currentUserId = getCurrentUserId();
        return (int) preferenceRepository.countByUserIdAndPreferenceType(currentUserId, PreferenceType.BOOKMARK);
    }

    public Integer getNotificationCount() {
        Integer currentUserId = getCurrentUserId();
        return (int) preferenceRepository.countByUserIdAndPreferenceTypeAndNotificationEnabled(
            currentUserId, PreferenceType.BOOKMARK, true);
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

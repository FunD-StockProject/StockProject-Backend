package com.fund.stockProject.global.scheduler;

import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fund.stockProject.keyword.repository.KeywordRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeywordCleanupScheduler {

    private final KeywordRepository keywordRepository;

    // 매주 실행하여 30일 이상 사용되지 않은 키워드 삭제
    @Scheduled(cron = "0 0 0 * * MON") // 매주 월요일 0시 실행
    @Transactional
    public void cleanupUnusedKeywords() {
        final LocalDate cutoffDate = LocalDate.now().minusDays(30);
        final int agedOrphanDeleted = keywordRepository.deleteOrphanKeywordsByLastUsedAtBefore(cutoffDate);
        final int orphanDeleted = keywordRepository.deleteAllOrphanKeywords();

        log.info("Unused keywords cleanup completed - agedOrphanDeleted: {}, orphanDeleted: {}",
            agedOrphanDeleted, orphanDeleted);
    }
}

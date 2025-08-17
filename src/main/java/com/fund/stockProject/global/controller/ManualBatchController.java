package com.fund.stockProject.global.controller;

import com.fund.stockProject.global.scheduler.BatchScheduler;
import com.fund.stockProject.global.scheduler.ScoreUpdateScheduler;
import com.fund.stockProject.score.service.ScoreService;
import com.fund.stockProject.stock.domain.COUNTRY;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/manual-batch")
public class ManualBatchController {

    private final ScoreUpdateScheduler scoreUpdateScheduler;
    private final BatchScheduler batchScheduler;
    private final ScoreService scoreService;

    // 해외 점수 & 키워드 수동 실행 (미처리 건만)
    @PostMapping("/scores/oversea")
    public ResponseEntity<String> runScoresOversea() {
        scoreUpdateScheduler.processScoresOversea();
        return ResponseEntity.ok("Triggered: scores oversea");
    }

    // 국내 점수 & 키워드 수동 실행 (미처리 건만)
    @PostMapping("/scores/korea")
    public ResponseEntity<String> runScoresKorea() {
        scoreUpdateScheduler.processScoresKorea();
        return ResponseEntity.ok("Triggered: scores korea");
    }

    // 공포지수, 지수 수동 실행
    @PostMapping("/scores/indexes")
    public ResponseEntity<String> runIndexScores() {
        scoreUpdateScheduler.processIndexScores();
        return ResponseEntity.ok("Triggered: index scores");
    }

    // 주식 심볼/이름 업데이트 배치 수동 실행
    @PostMapping("/stock/symbol-name")
    public ResponseEntity<String> runUpdateStockSymbolNameJob() {
        batchScheduler.runUpdateSymbolNameJob();
        return ResponseEntity.ok("Triggered: updateStockSymbolNameJob");
    }

    // ================= 강제 재계산 =================
    // 오늘 데이터가 있어도 삭제 후 재계산(덮어쓰기)

    // 해외 전체 강제 재계산
    @PostMapping("/scores/oversea/force")
    public ResponseEntity<String> forceScoresOversea() {
        scoreService.forceUpdateAllByCountry(COUNTRY.OVERSEA);
        return ResponseEntity.ok("Forced: scores oversea");
    }

    // 국내 전체 강제 재계산
    @PostMapping("/scores/korea/force")
    public ResponseEntity<String> forceScoresKorea() {
        scoreService.forceUpdateAllByCountry(COUNTRY.KOREA);
        return ResponseEntity.ok("Forced: scores korea");
    }

    // 단건 강제 재계산 (예: /scores/123/force?country=OVERSEA)
    @PostMapping("/scores/{stockId}/force")
    public ResponseEntity<String> forceScoreSingle(@PathVariable Integer stockId,
                                                   @RequestParam COUNTRY country) {
        scoreService.forceUpdateOne(stockId, country);
        return ResponseEntity.ok("Forced: score " + stockId + " (" + country + ")");
    }
}

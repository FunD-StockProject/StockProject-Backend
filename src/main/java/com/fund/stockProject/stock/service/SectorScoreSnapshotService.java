package com.fund.stockProject.stock.service;

import com.fund.stockProject.score.entity.Score;
import com.fund.stockProject.score.repository.ScoreRepository;
import com.fund.stockProject.stock.domain.COUNTRY;
import com.fund.stockProject.stock.domain.DomesticSector;
import com.fund.stockProject.stock.domain.OverseasSector;
import com.fund.stockProject.stock.dto.response.SectorAverageResponse;
import com.fund.stockProject.stock.entity.SectorScoreSnapshot;
import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.repository.SectorScoreSnapshotRepository;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SectorScoreSnapshotService {

    private final ScoreRepository scoreRepository;
    private final SectorScoreSnapshotRepository snapshotRepository;

    public List<SectorAverageResponse> getLatestSectorAverages(COUNTRY country) {
        return snapshotRepository.findLatestDateByCountry(country)
            .map(date -> snapshotRepository.findByCountryAndDate(country, date).stream()
                .map(snapshot -> SectorAverageResponse.builder()
                    .sector(snapshot.getSector())
                    .sectorName(snapshot.getSectorName())
                    .averageScore(snapshot.getAverageScore())
                    .count(snapshot.getCount())
                    .build())
                .toList())
            .orElseGet(List::of);
    }

    public void saveDailySnapshot(COUNTRY country, LocalDate date) {
        if (country == COUNTRY.KOREA) {
            List<Score> scores = scoreRepository.findLatestValidScoresByCountryKorea();
            Map<DomesticSector, long[]> stats = new EnumMap<>(DomesticSector.class);
            for (Score score : scores) {
                Stock stock = score.getStock();
                if (stock == null) {
                    continue;
                }
                DomesticSector sector = stock.getDomesticSector();
                if (sector == null || sector == DomesticSector.UNKNOWN) {
                    continue;
                }
                long[] acc = stats.computeIfAbsent(sector, key -> new long[2]);
                acc[0] += score.getScoreKorea();
                acc[1] += 1;
            }
            upsertSnapshots(country, date, stats);
            return;
        }

        List<Score> scores = scoreRepository.findLatestValidScoresByCountryOversea();
        Map<OverseasSector, long[]> stats = new EnumMap<>(OverseasSector.class);
        for (Score score : scores) {
            Stock stock = score.getStock();
            if (stock == null) {
                continue;
            }
            OverseasSector sector = stock.getOverseasSector();
            if (sector == null || sector == OverseasSector.UNKNOWN) {
                continue;
            }
            long[] acc = stats.computeIfAbsent(sector, key -> new long[2]);
            acc[0] += score.getScoreOversea();
            acc[1] += 1;
        }
        upsertSnapshots(country, date, stats);
    }

    private void upsertSnapshots(COUNTRY country, LocalDate date, Map<? extends Enum<?>, long[]> stats) {
        for (Map.Entry<? extends Enum<?>, long[]> entry : stats.entrySet()) {
            Enum<?> sector = entry.getKey();
            long[] acc = entry.getValue();
            long count = acc[1];
            if (count == 0) {
                continue;
            }
            int average = (int) Math.round(acc[0] / (double) count);
            String sectorKey = sector.name();
            String sectorName = resolveSectorName(sector);

            SectorScoreSnapshot snapshot = snapshotRepository
                .findByCountryAndDateAndSector(country, date, sectorKey)
                .orElseGet(() -> new SectorScoreSnapshot(date, country, sectorKey, sectorName, average, (int) count));

            snapshot.update(average, (int) count, sectorName);
            snapshotRepository.save(snapshot);
        }

        log.info("Sector score snapshot saved: country={}, date={}, sectors={}", country, date, stats.size());
    }

    private String resolveSectorName(Enum<?> sector) {
        if (sector instanceof DomesticSector domestic) {
            return domestic.getName();
        }
        if (sector instanceof OverseasSector overseas) {
            return overseas.getName();
        }
        return sector.name();
    }
}

package com.fund.stockProject.stock.batch;

import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockItemWriter implements ItemWriter<Stock> {

    private final StockRepository stockRepository;

    @Override
    public void write(Chunk<? extends Stock> chunk) {
        stockRepository.saveAll(chunk);
    }
}

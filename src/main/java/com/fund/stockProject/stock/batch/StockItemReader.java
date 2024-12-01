package com.fund.stockProject.stock.batch;

import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.repository.StockRepository;
import java.util.Iterator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockItemReader implements ItemReader<Stock> {

    private final StockRepository stockRepository;
    private Iterator<Stock> stockIterator;

    @Override
    public Stock read() {
        if (stockIterator == null || !stockIterator.hasNext()) {
            List<Stock> stockToUpdate = stockRepository.findStockBySymbolNameIsNull();
            stockIterator = stockToUpdate.iterator();
        }

        return stockIterator.hasNext() ? stockIterator.next() : null;
    }
}

package com.fund.stockProject.stock.batch;

import com.fund.stockProject.stock.entity.Stock;
import com.fund.stockProject.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockItemProcessor implements ItemProcessor<Stock, Stock> {

    private final StockService stockService;

    @Override
    public Stock process(Stock stock) {
        if (stock.getSymbolName() == null) {
            String symbolName = stockService.fetchSymbolName(stock.getSymbol(), stock.getExchangeNum());

            if (symbolName == null) {
                symbolName = stock.getSymbol();
            }

            stock.updateSymbolNameIfNull(symbolName);
        }

        return stock;
    }
}

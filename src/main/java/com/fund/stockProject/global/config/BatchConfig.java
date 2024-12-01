package com.fund.stockProject.global.config;

import com.fund.stockProject.stock.entity.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class BatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager platformTransactionManager;

    @Bean
    public Job updateStockSymbolNameJob(Step updateSymbolNameStep) {
        return new JobBuilder("updateStockSymbolNameJob", jobRepository)
            .start(updateSymbolNameStep)
            .build();
    }

    @Bean
    public Step updateSymbolNameStep(ItemReader<Stock> itemReader, ItemProcessor<Stock, Stock> itemProcessor, ItemWriter<Stock> itemWriter) {
        return new StepBuilder("updateSymbolNameStep", jobRepository)
            .<Stock, Stock>chunk(10, platformTransactionManager)
            .reader(itemReader)
            .processor(itemProcessor)
            .writer(itemWriter)
            .build();
    }
}

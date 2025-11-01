package com.fund.stockProject.global.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BatchScheduler {

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final Job updateStockSymbolNameJob;

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void runUpdateSymbolNameJob() {
        log.info("Starting updateStockSymbolNameJob scheduler");
        
        if (jobExplorer.findRunningJobExecutions("updateStockSymbolNameJob").isEmpty()) {
            final JobParametersBuilder jobParametersBuilder = new JobParametersBuilder();
            jobParametersBuilder.addLong("timestamp", System.currentTimeMillis()).toJobParameters();

            try {
                log.info("Launching updateStockSymbolNameJob");
                jobLauncher.run(updateStockSymbolNameJob, jobParametersBuilder.toJobParameters());
                log.info("updateStockSymbolNameJob completed successfully");
            } catch (JobExecutionAlreadyRunningException | JobRestartException |
                     JobInstanceAlreadyCompleteException | JobParametersInvalidException e) {
                log.error("Failed to launch updateStockSymbolNameJob", e);
                throw new RuntimeException(e);
            }
        } else {
            log.warn("updateStockSymbolNameJob is already running. Skipping execution.");
        }
    }
}

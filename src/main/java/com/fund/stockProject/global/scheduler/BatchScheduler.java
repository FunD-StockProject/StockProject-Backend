package com.fund.stockProject.global.scheduler;

import lombok.RequiredArgsConstructor;
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

@Component
@RequiredArgsConstructor
public class BatchScheduler {

    private final JobLauncher jobLauncher;
    private final JobExplorer jobExplorer;
    private final Job updateStockSymbolNameJob;

    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void runUpdateSymbolNameJob() {
        if (jobExplorer.findRunningJobExecutions("updateStockSymbolNameJob").isEmpty()) {
            final JobParametersBuilder jobParametersBuilder = new JobParametersBuilder();
            jobParametersBuilder.addLong("timestamp", System.currentTimeMillis()).toJobParameters();

            try {
                jobLauncher.run(updateStockSymbolNameJob, jobParametersBuilder.toJobParameters());
            } catch (JobExecutionAlreadyRunningException | JobRestartException |
                     JobInstanceAlreadyCompleteException | JobParametersInvalidException e) {
                throw new RuntimeException(e);
            }
        } else {
            System.out.println(jobExplorer.getLastJobInstance("updateStockSymbolNameJob")+" Job is already running. Skipping execution.");

        }
    }
}

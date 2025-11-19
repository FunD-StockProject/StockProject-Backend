package com.fund.stockProject.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

@Configuration
@EnableScheduling
public class SchedulerConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        ThreadPoolTaskScheduler threadPool = new ThreadPoolTaskScheduler();

        // 중요: t2.micro (1 vCPU, 1GB RAM) 환경을 고려한 설정
        // 너무 높으면 OOM(Out of Memory) 발생 위험이 있음 -> 4개 정도가 안전
        int POOL_SIZE = 4;

        threadPool.setPoolSize(POOL_SIZE);

        // 로그에서 스레드 이름으로 구분하기 쉽도록 설정 (예: my-scheduler-1)
        threadPool.setThreadNamePrefix("my-scheduler-");

        // 애플리케이션 종료 시(배포 등) 진행 중인 작업이 있다면 끝날 때까지 대기
        threadPool.setWaitForTasksToCompleteOnShutdown(true);
        threadPool.setAwaitTerminationSeconds(60); // 최대 60초 대기

        threadPool.initialize();

        taskRegistrar.setTaskScheduler(threadPool);
    }
}
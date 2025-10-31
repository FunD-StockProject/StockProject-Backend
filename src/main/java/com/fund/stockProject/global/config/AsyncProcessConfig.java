package com.fund.stockProject.global.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AsyncProcessConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService pythonExecutorService() {
        // 외부 프로세스 출력 소비용 제한된 풀 (동시 2개)
        return Executors.newFixedThreadPool(2);
    }

    @Bean
    public Semaphore pythonProcessSemaphore() {
        // 동시에 실행될 수 있는 파이썬 프로세스 상한
        return new Semaphore(2);
    }
}



package io.settlementhub.common.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class SettlementExecutorConfig {

    @Bean(name = "settlementExecutor")
    public ThreadPoolTaskExecutor settlementExecutor(AppProperties appProperties) {
        AppProperties.Settlement settlement = appProperties.settlement();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(settlement.corePoolSize());
        executor.setMaxPoolSize(settlement.maxPoolSize());
        executor.setQueueCapacity(settlement.queueCapacity());
        executor.setThreadNamePrefix("settlement-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}

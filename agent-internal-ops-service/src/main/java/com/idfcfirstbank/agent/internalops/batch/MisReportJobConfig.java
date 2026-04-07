package com.idfcfirstbank.agent.internalops.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.Map;

/**
 * Spring Batch job configuration for generating MIS reports.
 * Queries core-banking MCP for branch transaction data and aggregates results.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MisReportJobConfig {

    private final RestTemplate restTemplate;

    @Value("${agent.mcp.core-banking-url:http://mcp-core-banking-server:8086}")
    private String coreBankingUrl;

    @Bean
    public Job misReportJob(JobRepository jobRepository, Step fetchTransactionsStep) {
        return new JobBuilder("misReportJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(fetchTransactionsStep)
                .build();
    }

    @Bean
    public Step fetchTransactionsStep(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager) {
        return new StepBuilder("fetchTransactionsStep", jobRepository)
                .tasklet(fetchTransactionsTasklet(), transactionManager)
                .build();
    }

    @Bean
    public Tasklet fetchTransactionsTasklet() {
        return (contribution, chunkContext) -> {
            String branchId = chunkContext.getStepContext()
                    .getJobParameters()
                    .getOrDefault("branchId", "ALL")
                    .toString();

            String date = chunkContext.getStepContext()
                    .getJobParameters()
                    .getOrDefault("date", LocalDate.now().toString())
                    .toString();

            log.info("MIS Report Batch: Fetching transactions for branch={}, date={}", branchId, date);

            try {
                String url = coreBankingUrl + "/api/v1/core-banking/branch-transactions";
                Map<String, String> body = Map.of("branchId", branchId, "date", date);

                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);

                if (response != null) {
                    log.info("MIS Report Batch: Retrieved {} transactions for branch {}",
                            response.getOrDefault("totalTransactions", 0), branchId);
                    chunkContext.getStepContext().getStepExecution()
                            .getExecutionContext().put("reportData", response.toString());
                }
            } catch (Exception e) {
                log.error("MIS Report Batch: Failed to fetch transactions: {}", e.getMessage());
            }

            return RepeatStatus.FINISHED;
        };
    }
}

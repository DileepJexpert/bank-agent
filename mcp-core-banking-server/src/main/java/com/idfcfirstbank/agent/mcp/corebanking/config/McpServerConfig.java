package com.idfcfirstbank.agent.mcp.corebanking.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Configuration for the MCP Core Banking Server.
 * <p>
 * Provides the WebClient bean used to call the Finacle core banking API
 * and any other infrastructure beans.
 */
@Configuration
public class McpServerConfig {

    @Value("${finacle.base-url:http://localhost:9090}")
    private String finacleBaseUrl;

    /**
     * WebClient for calling the Finacle core banking system.
     * Configured with connection and read timeouts.
     */
    @Bean
    public WebClient finacleWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(10));

        return WebClient.builder()
                .baseUrl(finacleBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}

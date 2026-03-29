package com.idfcfirstbank.agent.mcp.cardmgmt.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Configuration for the MCP Card Management Server.
 * <p>
 * Provides the WebClient bean used to call the card management backend API
 * and configures resilience patterns via Resilience4j.
 */
@Configuration
public class McpCardConfig {

    @Value("${card-backend.base-url:http://localhost:9091}")
    private String cardBackendBaseUrl;

    /**
     * WebClient for calling the card management backend system.
     * Configured with connection and read timeouts.
     */
    @Bean
    public WebClient cardBackendWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(10));

        return WebClient.builder()
                .baseUrl(cardBackendBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}

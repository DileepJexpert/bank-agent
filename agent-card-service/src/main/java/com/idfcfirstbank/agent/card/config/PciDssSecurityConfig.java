package com.idfcfirstbank.agent.card.config;

import com.idfcfirstbank.agent.card.filter.CardDataMaskingFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * PCI-DSS security configuration for enhanced card data protection.
 * <p>
 * Active only under the {@code pci-dss} profile. Applies:
 * <ul>
 *   <li>Extra security headers (no-store cache, no-sniff, strict transport)</li>
 *   <li>Card data masking filter on all API responses</li>
 *   <li>Disabled caching of any card data</li>
 * </ul>
 */
@Slf4j
@Configuration
@Profile("pci-dss")
public class PciDssSecurityConfig {

    /**
     * Filter that adds PCI-DSS compliant security headers to every response.
     * Prevents browsers and intermediaries from caching or sniffing card data.
     */
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> pciDssHeaderFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
                response.setHeader("Pragma", "no-cache");
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("X-Frame-Options", "DENY");
                response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
                response.setHeader("X-XSS-Protection", "1; mode=block");
                response.setHeader("Content-Security-Policy", "default-src 'self'");
                response.setHeader("Referrer-Policy", "no-referrer");

                filterChain.doFilter(request, response);
            }
        });
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/api/*");
        registration.setName("pciDssHeaderFilter");

        log.info("PCI-DSS security header filter registered for /api/* endpoints");
        return registration;
    }

    /**
     * Register the card data masking filter that scans all outbound responses
     * for unmasked card numbers, CVVs, and expiry dates.
     */
    @Bean
    public FilterRegistrationBean<CardDataMaskingFilter> cardDataMaskingFilterRegistration(
            CardDataMaskingFilter cardDataMaskingFilter) {
        FilterRegistrationBean<CardDataMaskingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(cardDataMaskingFilter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns("/api/*");
        registration.setName("cardDataMaskingFilter");

        log.info("PCI-DSS card data masking filter registered for /api/* endpoints");
        return registration;
    }
}

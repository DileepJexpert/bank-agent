package com.idfcfirstbank.agent.mcp.cardmgmt.client;

import com.idfcfirstbank.agent.mcp.cardmgmt.model.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client for the card management backend system.
 * <p>
 * Applies resilience patterns via Resilience4j annotations:
 * <ul>
 *   <li>{@code @CircuitBreaker} - Prevents cascading failures when the backend is down</li>
 *   <li>{@code @Retry} - Retries transient failures with exponential backoff</li>
 *   <li>{@code @RateLimiter} - Protects the backend from excessive load</li>
 * </ul>
 * <p>
 * Currently returns mock data. When the card management backend integration is ready,
 * replace the mock implementations with actual WebClient calls.
 * <p>
 * CRITICAL: Full card numbers are NEVER logged or returned. All methods work with
 * cardLast4 only. Any card number data received from the backend is masked before
 * being returned to callers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardSystemClient {

    private final WebClient cardBackendWebClient;

    // ── Activate Card ──

    @CircuitBreaker(name = "cardBackend", fallbackMethod = "activateCardFallback")
    @Retry(name = "cardBackend")
    @RateLimiter(name = "cardBackend")
    public CardActivation activateCard(String customerId, String cardLast4) {
        log.info("Activating card via backend: customerId={}, cardLast4=****{}", customerId, cardLast4);

        // TODO: Replace with actual backend API call
        // return cardBackendWebClient.post()
        //         .uri("/api/cards/activate")
        //         .bodyValue(Map.of("customerId", customerId, "cardLast4", cardLast4))
        //         .retrieve()
        //         .bodyToMono(CardActivation.class)
        //         .block();

        // Mock response
        return new CardActivation(
                "ACTIVATED",
                LocalDateTime.now(),
                cardLast4
        );
    }

    @SuppressWarnings("unused")
    private CardActivation activateCardFallback(String customerId, String cardLast4, Throwable t) {
        log.error("Card backend activateCard circuit breaker fallback: customerId={}, error={}",
                customerId, t.getMessage());
        return new CardActivation("FAILED", LocalDateTime.now(), cardLast4);
    }

    // ── Block Card ──

    @CircuitBreaker(name = "cardBackend", fallbackMethod = "blockCardFallback")
    @Retry(name = "cardBackend")
    @RateLimiter(name = "cardBackend")
    public CardBlock blockCard(String customerId, String cardLast4, String reason) {
        log.info("Blocking card via backend: customerId={}, cardLast4=****{}, reason={}",
                customerId, cardLast4, reason);

        // Mock response
        String blockReference = "BLK" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        return new CardBlock(
                true,
                blockReference,
                LocalDateTime.now(),
                cardLast4,
                reason
        );
    }

    @SuppressWarnings("unused")
    private CardBlock blockCardFallback(String customerId, String cardLast4, String reason, Throwable t) {
        log.error("Card backend blockCard circuit breaker fallback: customerId={}, error={}",
                customerId, t.getMessage());
        return new CardBlock(false, null, LocalDateTime.now(), cardLast4, reason);
    }

    // ── Change Limit ──

    @CircuitBreaker(name = "cardBackend", fallbackMethod = "changeLimitFallback")
    @Retry(name = "cardBackend")
    @RateLimiter(name = "cardBackend")
    public Map<String, Object> changeLimit(String customerId, String cardLast4,
                                            String newLimit, String limitType) {
        log.info("Changing card limit via backend: customerId={}, cardLast4=****{}, newLimit={}, type={}",
                customerId, cardLast4, newLimit, limitType);

        // Mock response
        return Map.of(
                "approved", true,
                "newLimit", new BigDecimal(newLimit),
                "limitType", limitType,
                "effectiveFrom", LocalDateTime.now().toString(),
                "cardLast4", cardLast4
        );
    }

    @SuppressWarnings("unused")
    private Map<String, Object> changeLimitFallback(String customerId, String cardLast4,
                                                     String newLimit, String limitType, Throwable t) {
        log.error("Card backend changeLimit circuit breaker fallback: customerId={}, error={}",
                customerId, t.getMessage());
        return Map.of("approved", false, "reason", "Service temporarily unavailable");
    }

    // ── Get Reward Points ──

    @CircuitBreaker(name = "cardBackend", fallbackMethod = "getRewardPointsFallback")
    @Retry(name = "cardBackend")
    @RateLimiter(name = "cardBackend")
    @Cacheable(value = "rewardPoints", key = "#customerId", unless = "#result == null")
    public RewardPoints getRewardPoints(String customerId) {
        log.info("Fetching reward points from backend: customerId={}", customerId);

        // Mock response
        List<RewardPoints.RecentEarning> recentEarnings = List.of(
                new RewardPoints.RecentEarning("Amazon Purchase", 150, LocalDate.now().minusDays(2).toString()),
                new RewardPoints.RecentEarning("Fuel Station", 75, LocalDate.now().minusDays(5).toString()),
                new RewardPoints.RecentEarning("Restaurant", 200, LocalDate.now().minusDays(7).toString()),
                new RewardPoints.RecentEarning("Online Shopping", 300, LocalDate.now().minusDays(10).toString())
        );

        return new RewardPoints(
                12450L,
                new BigDecimal("3112.50"),
                LocalDate.now().plusMonths(6).toString(),
                recentEarnings
        );
    }

    @SuppressWarnings("unused")
    private RewardPoints getRewardPointsFallback(String customerId, Throwable t) {
        log.error("Card backend getRewardPoints circuit breaker fallback: customerId={}, error={}",
                customerId, t.getMessage());
        return new RewardPoints(0L, BigDecimal.ZERO, "N/A", List.of());
    }

    // ── Redeem Points ──

    @CircuitBreaker(name = "cardBackend", fallbackMethod = "redeemPointsFallback")
    @Retry(name = "cardBackend")
    @RateLimiter(name = "cardBackend")
    public Map<String, Object> redeemPoints(String customerId, String points, String redemptionType) {
        log.info("Redeeming points via backend: customerId={}, points={}, type={}",
                customerId, points, redemptionType);

        // Mock response
        String redemptionRef = "RDM" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        long pointsRedeemed = Long.parseLong(points);
        return Map.of(
                "redeemed", true,
                "pointsRedeemed", pointsRedeemed,
                "remainingPoints", 12450L - pointsRedeemed,
                "redemptionRef", redemptionRef,
                "redemptionType", redemptionType
        );
    }

    @SuppressWarnings("unused")
    private Map<String, Object> redeemPointsFallback(String customerId, String points,
                                                      String redemptionType, Throwable t) {
        log.error("Card backend redeemPoints circuit breaker fallback: customerId={}, error={}",
                customerId, t.getMessage());
        return Map.of("redeemed", false, "reason", "Service temporarily unavailable");
    }

    // ── Raise Dispute ──

    @CircuitBreaker(name = "cardBackend", fallbackMethod = "raiseDisputeFallback")
    @Retry(name = "cardBackend")
    @RateLimiter(name = "cardBackend")
    public DisputeResult raiseDispute(String customerId, String transactionId,
                                       String reason, String amount) {
        log.info("Raising dispute via backend: customerId={}, transactionId={}, amount={}",
                customerId, transactionId, amount);

        // Mock response
        String disputeId = "DSP" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
        return new DisputeResult(
                disputeId,
                "REGISTERED",
                LocalDate.now().plusDays(15).toString(),
                transactionId,
                new BigDecimal(amount)
        );
    }

    @SuppressWarnings("unused")
    private DisputeResult raiseDisputeFallback(String customerId, String transactionId,
                                                String reason, String amount, Throwable t) {
        log.error("Card backend raiseDispute circuit breaker fallback: customerId={}, error={}",
                customerId, t.getMessage());
        return new DisputeResult(null, "FAILED", null, transactionId, BigDecimal.ZERO);
    }

    // ── Convert to EMI ──

    @CircuitBreaker(name = "cardBackend", fallbackMethod = "convertToEMIFallback")
    @Retry(name = "cardBackend")
    @RateLimiter(name = "cardBackend")
    public EMIConversion convertToEMI(String customerId, String transactionId, String tenure) {
        log.info("Converting to EMI via backend: customerId={}, transactionId={}, tenure={}",
                customerId, transactionId, tenure);

        // Mock response
        int tenureMonths = Integer.parseInt(tenure);
        BigDecimal totalAmount = new BigDecimal("25000.00");
        BigDecimal interestRate = new BigDecimal("14.99");
        BigDecimal totalInterest = totalAmount.multiply(interestRate).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal totalCost = totalAmount.add(totalInterest);
        BigDecimal emiAmount = totalCost.divide(new BigDecimal(tenureMonths), 2, java.math.RoundingMode.HALF_UP);
        String emiId = "EMI" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();

        return new EMIConversion(
                emiAmount,
                interestRate,
                totalCost,
                emiId,
                tenureMonths,
                transactionId
        );
    }

    @SuppressWarnings("unused")
    private EMIConversion convertToEMIFallback(String customerId, String transactionId,
                                                String tenure, Throwable t) {
        log.error("Card backend convertToEMI circuit breaker fallback: customerId={}, error={}",
                customerId, t.getMessage());
        return new EMIConversion(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, 0, transactionId);
    }
}

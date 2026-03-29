package com.idfcfirstbank.agent.vault.identity.service;

import com.idfcfirstbank.agent.vault.identity.model.dto.AuthResponse;
import com.idfcfirstbank.agent.vault.identity.model.dto.CustomerVerifyRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerAuthService {

    private static final String OTP_PREFIX = "otp:";
    private static final long CUSTOMER_TOKEN_EXPIRY_SECONDS = 1800L; // 30 min

    private final StringRedisTemplate redisTemplate;
    private final TokenService tokenService;

    /**
     * Verifies a customer via OTP and generates a customer session token.
     * The OTP is validated against Redis where it was stored during the OTP send flow.
     * On successful verification, a JWT is issued containing the customer ID and risk level.
     *
     * @param request the customer verification request with customerId, otp, and channel
     * @return AuthResponse with a session token
     * @throws SecurityException if OTP verification fails
     */
    public AuthResponse verifyCustomer(CustomerVerifyRequest request) {
        log.info("Verifying customer: {} via channel: {}", request.customerId(), request.channel());

        // Retrieve stored OTP from Redis
        String otpKey = OTP_PREFIX + request.customerId();
        String storedOtp = redisTemplate.opsForValue().get(otpKey);

        if (storedOtp == null) {
            log.warn("No OTP found for customer: {} - may have expired", request.customerId());
            throw new SecurityException("OTP expired or not found. Please request a new OTP.");
        }

        if (!storedOtp.equals(request.otp())) {
            log.warn("OTP mismatch for customer: {}", request.customerId());
            throw new SecurityException("Invalid OTP");
        }

        // OTP verified - delete it to prevent reuse
        redisTemplate.delete(otpKey);

        // Determine risk level based on channel and customer profile
        String riskLevel = assessRiskLevel(request.customerId(), request.channel());

        String token = tokenService.generateCustomerToken(
                request.customerId(),
                riskLevel,
                request.channel()
        );

        log.info("Customer verified successfully: {} with risk level: {}",
                request.customerId(), riskLevel);

        return new AuthResponse(
                token,
                CUSTOMER_TOKEN_EXPIRY_SECONDS,
                List.of("customer:read", "customer:transact")
        );
    }

    /**
     * Assesses risk level for a customer session based on channel and profile data.
     * In production, this would integrate with a risk scoring engine.
     */
    private String assessRiskLevel(String customerId, String channel) {
        // Default risk assessment based on channel
        return switch (channel.toLowerCase()) {
            case "mobile" -> "LOW";
            case "web" -> "MEDIUM";
            case "api" -> "HIGH";
            default -> "MEDIUM";
        };
    }
}

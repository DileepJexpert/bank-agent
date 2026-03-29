package com.idfcfirstbank.agent.card.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servlet filter that scans outbound HTTP response bodies for unmasked card data
 * and replaces any detected card numbers, CVVs, or expiry dates with masked values.
 * <p>
 * This is a defence-in-depth measure. Upstream services should already mask card data,
 * but this filter acts as a safety net to ensure no PCI-sensitive data ever leaves the
 * service boundary unmasked.
 * <p>
 * Patterns detected and masked:
 * <ul>
 *   <li>16-digit card numbers (contiguous or separated by spaces/hyphens)</li>
 *   <li>CVV/CVC codes (3 or 4 digits in known JSON key contexts)</li>
 *   <li>Expiry dates in MM/YY or MM/YYYY format in known JSON key contexts</li>
 * </ul>
 */
@Slf4j
@Component
public class CardDataMaskingFilter extends OncePerRequestFilter {

    /**
     * Matches 16-digit card numbers: contiguous, space-separated, or hyphen-separated.
     * Covers Visa (4xxx), Mastercard (5xxx), Amex (3xxx), RuPay (6xxx) prefixes.
     */
    private static final Pattern CARD_NUMBER_CONTIGUOUS = Pattern.compile(
            "\\b([3-6]\\d{3})(\\d{4})(\\d{4})(\\d{4})\\b");

    private static final Pattern CARD_NUMBER_SPACED = Pattern.compile(
            "\\b([3-6]\\d{3})[ -](\\d{4})[ -](\\d{4})[ -](\\d{4})\\b");

    /**
     * Matches CVV/CVC values in JSON contexts like "cvv":"123" or "cvc": "1234".
     */
    private static final Pattern CVV_PATTERN = Pattern.compile(
            "(?i)(\"(?:cvv|cvc|cvv2|cvc2|securityCode)\"\\s*:\\s*\")\\d{3,4}(\")");

    /**
     * Matches expiry dates in JSON contexts like "expiry":"12/26" or "expiryDate": "12/2026".
     */
    private static final Pattern EXPIRY_PATTERN = Pattern.compile(
            "(?i)(\"(?:expiry|expiryDate|expDate|cardExpiry|validThru)\"\\s*:\\s*\")\\d{2}/\\d{2,4}(\")");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        filterChain.doFilter(request, responseWrapper);

        byte[] responseBody = responseWrapper.getContentAsByteArray();

        if (responseBody.length > 0) {
            String contentType = responseWrapper.getContentType();
            boolean isTextContent = contentType != null && (
                    contentType.contains("json") ||
                    contentType.contains("text") ||
                    contentType.contains("xml"));

            if (isTextContent) {
                String original = new String(responseBody, StandardCharsets.UTF_8);
                String masked = maskCardData(original);

                if (!original.equals(masked)) {
                    log.warn("PCI-DSS ALERT: Unmasked card data detected in response to {} {}. "
                                    + "Data has been masked before transmission.",
                            request.getMethod(), request.getRequestURI());
                }

                byte[] maskedBytes = masked.getBytes(StandardCharsets.UTF_8);
                response.setContentLength(maskedBytes.length);
                response.getOutputStream().write(maskedBytes);
            } else {
                responseWrapper.copyBodyToResponse();
            }
        } else {
            responseWrapper.copyBodyToResponse();
        }
    }

    /**
     * Apply all card data masking patterns to the given text.
     *
     * @param text the response body text
     * @return the text with all card-sensitive data masked
     */
    String maskCardData(String text) {
        String result = text;

        // Mask space/hyphen-separated card numbers first (more specific pattern)
        result = maskCardNumbersFormatted(result);

        // Mask contiguous 16-digit card numbers
        result = maskCardNumbersContiguous(result);

        // Mask CVV values in JSON key contexts
        result = maskCvv(result);

        // Mask expiry dates in JSON key contexts
        result = maskExpiry(result);

        return result;
    }

    private String maskCardNumbersFormatted(String text) {
        Matcher matcher = CARD_NUMBER_SPACED.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String lastFour = matcher.group(4);
            matcher.appendReplacement(sb, "XXXX-XXXX-XXXX-" + lastFour);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String maskCardNumbersContiguous(String text) {
        Matcher matcher = CARD_NUMBER_CONTIGUOUS.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String lastFour = matcher.group(4);
            matcher.appendReplacement(sb, "XXXX-XXXX-XXXX-" + lastFour);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String maskCvv(String text) {
        Matcher matcher = CVV_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, matcher.group(1) + "***" + matcher.group(2));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String maskExpiry(String text) {
        Matcher matcher = EXPIRY_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, matcher.group(1) + "**/**" + matcher.group(2));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}

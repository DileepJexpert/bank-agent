package com.idfcfirstbank.agent.common.util;

import java.util.regex.Pattern;

/**
 * Utility class for masking Personally Identifiable Information (PII) in strings.
 * Used by both application code and the {@link com.idfcfirstbank.agent.common.filter.PiiMaskingFilter}
 * to ensure sensitive data never reaches logs, API responses, or audit trails in cleartext.
 *
 * <p>Supported PII types:
 * <ul>
 *   <li>Aadhaar numbers (12-digit Indian national ID)</li>
 *   <li>PAN numbers (Indian Permanent Account Number, format: ABCDE1234F)</li>
 *   <li>Credit/debit card numbers (16 digits, with or without separators)</li>
 *   <li>Indian phone numbers (10 digits, optionally prefixed with +91 or 0)</li>
 *   <li>Email addresses</li>
 * </ul>
 */
public final class MaskingUtils {

    private MaskingUtils() {
        // utility class
    }

    // ── Aadhaar: 12 consecutive digits, possibly separated by spaces or hyphens ──
    private static final Pattern AADHAAR_PATTERN =
            Pattern.compile("\\b(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})\\b");

    // ── PAN: 5 uppercase letters, 4 digits, 1 uppercase letter ──
    private static final Pattern PAN_PATTERN =
            Pattern.compile("\\b([A-Z]{3})([A-Z]{2})(\\d{4})([A-Z])\\b");

    // ── Card number: 16 digits, possibly grouped by spaces or hyphens ──
    private static final Pattern CARD_PATTERN =
            Pattern.compile("\\b(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})\\b");

    // ── Phone: 10 digits, optionally prefixed with +91, 91, or 0 ──
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?:\\+91[- ]?|91[- ]?|0)?\\b(\\d{2})(\\d{4})(\\d{4})\\b");

    // ── Email: standard email pattern ──
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("\\b([a-zA-Z0-9._%+-]+)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})\\b");

    /**
     * Mask an Aadhaar number, keeping only the last 4 digits visible.
     * <p>Example: {@code "1234 5678 9012"} becomes {@code "XXXX XXXX 9012"}.
     *
     * @param input the input string potentially containing Aadhaar numbers
     * @return string with all Aadhaar numbers masked
     */
    public static String maskAadhaar(String input) {
        if (input == null) return null;
        return AADHAAR_PATTERN.matcher(input).replaceAll("XXXX XXXX $3");
    }

    /**
     * Mask a PAN number, keeping the first and last characters visible.
     * <p>Example: {@code "ABCDE1234F"} becomes {@code "A****234F"}.
     *
     * @param input the input string potentially containing PAN numbers
     * @return string with all PAN numbers masked
     */
    public static String maskPan(String input) {
        if (input == null) return null;
        return PAN_PATTERN.matcher(input).replaceAll(mr -> {
            String full = mr.group();
            return full.charAt(0) + "****" + full.substring(6);
        });
    }

    /**
     * Mask a card number, keeping only the last 4 digits visible.
     * <p>Example: {@code "4111 1111 1111 1234"} becomes {@code "XXXX XXXX XXXX 1234"}.
     *
     * @param input the input string potentially containing card numbers
     * @return string with all card numbers masked
     */
    public static String maskCardNumber(String input) {
        if (input == null) return null;
        return CARD_PATTERN.matcher(input).replaceAll("XXXX XXXX XXXX $4");
    }

    /**
     * Mask a phone number, keeping only the last 4 digits visible.
     * <p>Example: {@code "+91 9876543210"} becomes {@code "+91 XXXXXX3210"}.
     *
     * @param input the input string potentially containing phone numbers
     * @return string with all phone numbers masked
     */
    public static String maskPhone(String input) {
        if (input == null) return null;
        return PHONE_PATTERN.matcher(input).replaceAll("XXXXXX$3");
    }

    /**
     * Mask an email address, keeping the first character of the local part and the domain.
     * <p>Example: {@code "john.doe@example.com"} becomes {@code "j***@example.com"}.
     *
     * @param input the input string potentially containing email addresses
     * @return string with all email addresses masked
     */
    public static String maskEmail(String input) {
        if (input == null) return null;
        return EMAIL_PATTERN.matcher(input).replaceAll(mr -> {
            String localPart = mr.group(1);
            String domain = mr.group(2);
            return localPart.charAt(0) + "***@" + domain;
        });
    }

    /**
     * Apply all masking rules in sequence. This is the preferred method for
     * general-purpose PII scrubbing (e.g. in log filters and audit payloads).
     *
     * <p>Order matters: card numbers are masked before phone numbers because
     * a 16-digit card number could otherwise partially match the phone pattern.
     *
     * @param input the input string potentially containing multiple PII types
     * @return string with all recognised PII masked
     */
    public static String maskAll(String input) {
        if (input == null) return null;
        String result = input;
        result = maskCardNumber(result);  // 16 digits before 12-digit Aadhaar
        result = maskAadhaar(result);
        result = maskPan(result);
        result = maskPhone(result);
        result = maskEmail(result);
        return result;
    }
}

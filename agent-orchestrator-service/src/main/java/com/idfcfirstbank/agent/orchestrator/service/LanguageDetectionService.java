package com.idfcfirstbank.agent.orchestrator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Simple keyword/character-based language detection service.
 * <p>
 * Detects: English (en), Hindi (hi), Hinglish (hi-en), Tamil (ta), Telugu (te),
 * Bengali (bn), Marathi (mr), Gujarati (gu).
 * Defaults to English if uncertain.
 */
@Slf4j
@Service
public class LanguageDetectionService {

    // Unicode ranges for Indic scripts
    private static final Pattern DEVANAGARI = Pattern.compile("[\\u0900-\\u097F]");
    private static final Pattern TAMIL = Pattern.compile("[\\u0B80-\\u0BFF]");
    private static final Pattern TELUGU = Pattern.compile("[\\u0C00-\\u0C7F]");
    private static final Pattern BENGALI = Pattern.compile("[\\u0980-\\u09FF]");
    private static final Pattern GUJARATI = Pattern.compile("[\\u0A80-\\u0AFF]");

    // Common Hinglish transliterations (Hindi words written in Latin script)
    private static final Set<String> HINGLISH_MARKERS = Set.of(
            "mera", "meri", "mere", "kya", "hai", "karo", "kaise", "kab",
            "nahi", "nahin", "haan", "ji", "aur", "yeh", "woh", "kaun",
            "kahan", "kyun", "kitna", "kitne", "kitni", "abhi", "kal",
            "aaj", "paisa", "paise", "rupaye", "khata", "bhai", "didi",
            "accha", "theek", "achha", "batao", "bolo", "chahiye", "dena",
            "lena", "wala", "wali", "apna", "apni", "humara", "tumhara",
            "bhi", "toh", "lekin", "agar", "jab", "phir", "bahut"
    );

    private static final int HINGLISH_THRESHOLD = 2;

    /**
     * Detect the language of the given text.
     *
     * @param text the input text
     * @return language code: en, hi, hi-en, ta, te, bn, mr, gu
     */
    public String detectLanguage(String text) {
        if (text == null || text.isBlank()) {
            return "en";
        }

        long devanagariCount = countMatches(DEVANAGARI, text);
        long tamilCount = countMatches(TAMIL, text);
        long teluguCount = countMatches(TELUGU, text);
        long bengaliCount = countMatches(BENGALI, text);
        long gujaratiCount = countMatches(GUJARATI, text);

        // Find the dominant Indic script
        long maxIndic = Math.max(devanagariCount,
                Math.max(tamilCount, Math.max(teluguCount, Math.max(bengaliCount, gujaratiCount))));

        if (maxIndic > 0) {
            // Significant Indic script presence
            if (tamilCount == maxIndic && tamilCount >= 3) {
                log.debug("Detected language: Tamil (ta)");
                return "ta";
            }
            if (teluguCount == maxIndic && teluguCount >= 3) {
                log.debug("Detected language: Telugu (te)");
                return "te";
            }
            if (bengaliCount == maxIndic && bengaliCount >= 3) {
                log.debug("Detected language: Bengali (bn)");
                return "bn";
            }
            if (gujaratiCount == maxIndic && gujaratiCount >= 3) {
                log.debug("Detected language: Gujarati (gu)");
                return "gu";
            }
            if (devanagariCount == maxIndic && devanagariCount >= 3) {
                // Devanagari is shared by Hindi and Marathi; use heuristic markers for Marathi
                if (isLikelyMarathi(text)) {
                    log.debug("Detected language: Marathi (mr)");
                    return "mr";
                }
                log.debug("Detected language: Hindi (hi)");
                return "hi";
            }
        }

        // Check for Hinglish (Latin script with Hindi transliterations)
        if (isHinglish(text)) {
            log.debug("Detected language: Hinglish (hi-en)");
            return "hi-en";
        }

        log.debug("Detected language: English (en) [default]");
        return "en";
    }

    private long countMatches(Pattern pattern, String text) {
        return pattern.matcher(text).results().count();
    }

    private boolean isHinglish(String text) {
        String lower = text.toLowerCase();
        String[] words = lower.split("\\s+");
        int hinglishWordCount = 0;

        for (String word : words) {
            // Strip common punctuation
            String clean = word.replaceAll("[^a-z]", "");
            if (HINGLISH_MARKERS.contains(clean)) {
                hinglishWordCount++;
            }
        }

        return hinglishWordCount >= HINGLISH_THRESHOLD;
    }

    /**
     * Heuristic to distinguish Marathi from Hindi when Devanagari script is detected.
     * Uses common Marathi-specific words/suffixes.
     */
    private boolean isLikelyMarathi(String text) {
        // Common Marathi markers not typically found in standard Hindi
        Set<String> marathiMarkers = Set.of(
                "\u0906\u0939\u0947",     // ahe (is)
                "\u0915\u0930\u093E",     // kara (do)
                "\u092E\u0932\u093E",     // mala (to me)
                "\u0924\u0941\u092E\u094D\u0939\u093E\u0932\u093E", // tumhala
                "\u0906\u092E\u094D\u0939\u093E\u0932\u093E",       // amhala
                "\u0939\u094B\u0924\u0947" // hote
        );

        for (String marker : marathiMarkers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}

package com.idfcfirstbank.agent.orchestrator.service;

import com.idfcfirstbank.agent.orchestrator.model.SessionInfo;
import com.idfcfirstbank.agent.orchestrator.model.SessionInfo.MessageEntry;
import com.idfcfirstbank.agent.orchestrator.statemachine.ConversationState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session management for customer conversations.
 * <p>
 * Uses Redis when available (production/Docker). Falls back to in-memory
 * storage when Redis is not configured (local debug mode).
 * <p>
 * For local debugging: run with {@code --spring.profiles.active=local}
 * to skip Redis entirely.
 */
@Slf4j
@Service
public class SessionService {

    private static final String SESSION_KEY_PREFIX = "session:";
    private static final Duration SESSION_TTL = Duration.ofHours(2);

    @Nullable
    private final RedisTemplate<String, Object> redisTemplate;

    /** In-memory fallback when Redis is unavailable. */
    private final Map<String, SessionInfo> inMemoryStore = new ConcurrentHashMap<>();
    private final boolean useRedis;

    public SessionService(@Nullable RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.useRedis = redisTemplate != null && isRedisAvailable(redisTemplate);
        if (useRedis) {
            log.info("SessionService using Redis for session storage");
        } else {
            log.info("SessionService using IN-MEMORY storage (no Redis). Sessions lost on restart.");
        }
    }

    private boolean isRedisAvailable(RedisTemplate<String, Object> template) {
        try {
            template.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            log.warn("Redis not available, falling back to in-memory sessions: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Create a new conversation session.
     */
    public SessionInfo createSession(String customerId, String channel, String language) {
        String sessionId = UUID.randomUUID().toString();
        SessionInfo session = new SessionInfo(
                sessionId,
                customerId,
                channel,
                language,
                ConversationState.IDLE.name(),
                new ArrayList<>(),
                Instant.now()
        );
        save(session);
        log.info("Created session: sessionId={}, customerId={}, channel={}, storage={}",
                sessionId, customerId, channel, useRedis ? "redis" : "in-memory");
        return session;
    }

    /**
     * Retrieve a session by its identifier.
     */
    public Optional<SessionInfo> getSession(String sessionId) {
        if (useRedis) {
            try {
                Object value = redisTemplate.opsForValue().get(keyOf(sessionId));
                if (value instanceof SessionInfo session) {
                    return Optional.of(session);
                }
            } catch (Exception e) {
                log.warn("Redis read failed, trying in-memory: {}", e.getMessage());
            }
        }
        return Optional.ofNullable(inMemoryStore.get(sessionId));
    }

    /**
     * Append a message to the conversation history and persist.
     */
    public SessionInfo addMessage(String sessionId, String role, String content) {
        SessionInfo session = getSession(sessionId).orElse(null);
        if (session == null) {
            // Auto-create session for convenience in local dev
            session = createSession("auto-user", "web", "en");
            sessionId = session.sessionId();
        }

        List<MessageEntry> updatedHistory = new ArrayList<>(session.history());
        updatedHistory.add(new MessageEntry(role, content, Instant.now()));

        SessionInfo updated = new SessionInfo(
                session.sessionId(),
                session.customerId(),
                session.channel(),
                session.language(),
                session.state(),
                updatedHistory,
                session.createdAt()
        );
        save(updated);
        return updated;
    }

    /**
     * Update the conversation state.
     */
    public SessionInfo updateState(String sessionId, ConversationState newState) {
        SessionInfo session = getSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        SessionInfo updated = new SessionInfo(
                session.sessionId(),
                session.customerId(),
                session.channel(),
                session.language(),
                newState.name(),
                session.history(),
                session.createdAt()
        );
        save(updated);
        return updated;
    }

    /**
     * End and remove a session.
     */
    public void endSession(String sessionId) {
        if (useRedis) {
            try {
                redisTemplate.delete(keyOf(sessionId));
            } catch (Exception e) {
                log.warn("Redis delete failed: {}", e.getMessage());
            }
        }
        inMemoryStore.remove(sessionId);
        log.info("Ended session: sessionId={}", sessionId);
    }

    /**
     * Get conversation history for a session.
     */
    public List<MessageEntry> getHistory(String sessionId) {
        return getSession(sessionId)
                .map(SessionInfo::history)
                .orElse(List.of());
    }

    private void save(SessionInfo session) {
        // Always save to in-memory (fast lookup / fallback)
        inMemoryStore.put(session.sessionId(), session);

        // Also save to Redis if available
        if (useRedis) {
            try {
                redisTemplate.opsForValue().set(keyOf(session.sessionId()), session, SESSION_TTL);
            } catch (Exception e) {
                log.warn("Redis write failed, session stored in-memory only: {}", e.getMessage());
            }
        }
    }

    private String keyOf(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }
}

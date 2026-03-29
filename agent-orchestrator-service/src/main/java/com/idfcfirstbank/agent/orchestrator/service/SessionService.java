package com.idfcfirstbank.agent.orchestrator.service;

import com.idfcfirstbank.agent.orchestrator.model.SessionInfo;
import com.idfcfirstbank.agent.orchestrator.model.SessionInfo.MessageEntry;
import com.idfcfirstbank.agent.orchestrator.statemachine.ConversationState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed session management for customer conversations.
 * Stores conversation context, message history, and current state machine position.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final String SESSION_KEY_PREFIX = "session:";
    private static final Duration SESSION_TTL = Duration.ofHours(2);

    private final RedisTemplate<String, Object> redisTemplate;

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
        log.info("Created session: sessionId={}, customerId={}, channel={}", sessionId, customerId, channel);
        return session;
    }

    /**
     * Retrieve a session by its identifier.
     */
    public Optional<SessionInfo> getSession(String sessionId) {
        Object value = redisTemplate.opsForValue().get(keyOf(sessionId));
        if (value instanceof SessionInfo session) {
            return Optional.of(session);
        }
        return Optional.empty();
    }

    /**
     * Append a message to the conversation history and persist.
     */
    public SessionInfo addMessage(String sessionId, String role, String content) {
        SessionInfo session = getSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

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
        redisTemplate.delete(keyOf(sessionId));
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
        redisTemplate.opsForValue().set(keyOf(session.sessionId()), session, SESSION_TTL);
    }

    private String keyOf(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }
}

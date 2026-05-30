package com.aptroid.pipeline.model;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a single customer behavior event flowing through the pipeline.
 *
 * DESIGN DECISIONS (mention these in interview):
 *
 * 1. IMMUTABLE — fields are final, no setters.
 *    Why: events are facts that happened. You don't change history.
 *    Immutable objects are also inherently thread-safe — multiple
 *    consumer threads can read this object simultaneously with zero risk.
 *
 * 2. METADATA MAP — flexible key-value store for extra context.
 *    Why: different event types carry different data. A PAGE_VIEW
 *    has a URL; a PURCHASE has an amount. Rather than 10 nullable
 *    fields, one map handles all cases cleanly.
 *
 * 3. UUID for eventId — globally unique, no coordination needed.
 *    Why: in a distributed system you can't use auto-increment IDs
 *    (two servers would collide). UUIDs are safe to generate anywhere.
 *
 * 4. Instant for timestamp — timezone-aware, nanosecond precision.
 *    Why: Instant is the right type for "a moment in time".
 *    Unlike Date or long, it's unambiguous and modern.
 */
public final class CustomerEvent {

    private final String eventId;       // Unique ID for this event
    private final String userId;        // Which customer triggered this
    private final EventType eventType;  // What they did
    private final Instant timestamp;    // Exactly when it happened
    private final Map<String, String> metadata; // Extra context (URL, amount, etc.)

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public CustomerEvent(String userId, EventType eventType, Map<String, String> metadata) {
        // Validate inputs — fail fast, fail loud
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be null or blank");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("eventType cannot be null");
        }

        this.eventId   = UUID.randomUUID().toString();
        this.userId    = userId;
        this.eventType = eventType;
        this.timestamp = Instant.now();

        // Defensive copy — caller can't mutate our internal map
        this.metadata  = metadata != null
                ? Collections.unmodifiableMap(new HashMap<>(metadata))
                : Collections.emptyMap();
    }

    // Convenience constructor — no metadata needed
    public CustomerEvent(String userId, EventType eventType) {
        this(userId, eventType, null);
    }

    // -------------------------------------------------------------------------
    // Getters — no setters (immutable)
    // -------------------------------------------------------------------------

    public String getEventId()              { return eventId; }
    public String getUserId()               { return userId; }
    public EventType getEventType()         { return eventType; }
    public Instant getTimestamp()           { return timestamp; }
    public Map<String, String> getMetadata(){ return metadata; }

    public String getMetadata(String key) {
        return metadata.getOrDefault(key, "");
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return String.format("[Event] id=%-36s | user=%-10s | type=%-14s | time=%s",
                eventId, userId, eventType, timestamp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomerEvent other)) return false;
        return eventId.equals(other.eventId);
    }

    @Override
    public int hashCode() {
        return eventId.hashCode();
    }
}

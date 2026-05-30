package com.aptroid.pipeline.model;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks the accumulated behavior history for a single customer.
 *
 * DESIGN DECISIONS (mention these in interview):
 *
 * 1. AtomicInteger for event counts — NOT int.
 *    Why: Multiple consumer threads update the same UserProfile
 *    concurrently. A plain int++ is NOT atomic — it's three CPU
 *    instructions (read, increment, write). Two threads can both
 *    read 5, both write 6, and you lose a count.
 *    AtomicInteger.incrementAndGet() is a single CPU instruction
 *    (CAS — Compare And Swap). Zero locks needed.
 *
 * 2. EnumMap for eventCounts — NOT HashMap.
 *    Why: Keys are enums. EnumMap stores them as a simple array
 *    indexed by ordinal — O(1) access with zero hashing overhead.
 *    Faster and more memory-efficient than HashMap for enum keys.
 *
 * 3. volatile for lastSeen — NOT a regular Instant.
 *    Why: One thread writes lastSeen, another reads it.
 *    Without volatile, the JVM can cache the old value in a
 *    CPU register and the reader never sees the update.
 *    volatile forces a memory barrier — every read hits main memory.
 *
 * 4. engagementScore — a derived metric.
 *    Different events have different weights. A PURCHASE is worth
 *    more signal than a PAGE_VIEW. The score lets rules reason
 *    about overall user value without checking every event type.
 */
public class UserProfile {

    private final String userId;

    // EnumMap: faster than HashMap for enum keys (array under the hood)
    private final Map<EventType, AtomicInteger> eventCounts;

    // volatile: visible across threads without synchronization
    private volatile Instant lastSeen;
    private volatile Instant firstSeen;

    // AtomicInteger: thread-safe without locks
    private final AtomicInteger totalEvents;
    private final AtomicInteger engagementScore;

    // Current segment — updated by SegmentClassifier
    private volatile UserSegment segment;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public UserProfile(String userId) {
        this.userId          = userId;
        this.eventCounts     = new EnumMap<>(EventType.class);
        this.totalEvents     = new AtomicInteger(0);
        this.engagementScore = new AtomicInteger(0);
        this.firstSeen       = Instant.now();
        this.lastSeen        = Instant.now();
        this.segment         = UserSegment.NEW_USER;

        // Pre-populate counts for all event types (avoid null checks later)
        for (EventType type : EventType.values()) {
            eventCounts.put(type, new AtomicInteger(0));
        }
    }

    // -------------------------------------------------------------------------
    // Core update method — called by the pipeline on every event
    // -------------------------------------------------------------------------

    /**
     * Records one event. Thread-safe — can be called from multiple
     * consumer threads simultaneously.
     */
    public void recordEvent(CustomerEvent event) {
        // Increment count for this specific event type
        eventCounts.get(event.getEventType()).incrementAndGet();

        // Increment total
        totalEvents.incrementAndGet();

        // Update last seen (volatile write — immediately visible to all threads)
        lastSeen = event.getTimestamp();

        // Update engagement score based on event weight
        engagementScore.addAndGet(getEventWeight(event.getEventType()));
    }

    // -------------------------------------------------------------------------
    // Event weights — different behaviors have different value signals
    // -------------------------------------------------------------------------

    private int getEventWeight(EventType type) {
        return switch (type) {
            case PURCHASE    -> 50;   // Highest signal — they bought!
            case CART_ADD    -> 20;   // Strong intent
            case PRICING_VIEW-> 15;   // High intent — researching price
            case EMAIL_CLICK -> 10;   // Engaged with marketing
            case EMAIL_OPEN  -> 5;    // Noticed the email
            case LOGIN       -> 5;    // Active user
            case CLICK       -> 3;    // General engagement
            case PAGE_VIEW   -> 1;    // Weakest signal
            default          -> 0;
        };
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getUserId()       { return userId; }
    public Instant getLastSeen()    { return lastSeen; }
    public Instant getFirstSeen()   { return firstSeen; }
    public int getTotalEvents()     { return totalEvents.get(); }
    public int getEngagementScore() { return engagementScore.get(); }
    public UserSegment getSegment() { return segment; }

    public int getEventCount(EventType type) {
        return eventCounts.getOrDefault(type, new AtomicInteger(0)).get();
    }

    public void setSegment(UserSegment segment) {
        this.segment = segment;  // volatile write
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return String.format(
            "UserProfile{userId='%s', totalEvents=%d, score=%d, segment=%s, lastSeen=%s}",
            userId, totalEvents.get(), engagementScore.get(), segment, lastSeen
        );
    }
}

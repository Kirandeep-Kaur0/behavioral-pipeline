package com.aptroid.pipeline;

import com.aptroid.pipeline.model.CustomerEvent;
import com.aptroid.pipeline.model.EventType;
import com.aptroid.pipeline.model.UserProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Day 1 Tests — CustomerEvent and UserProfile
 *
 * INTERVIEW TIP: When asked "how do you ensure code quality?"
 * point to these tests. Show you test edge cases (null inputs,
 * concurrent updates) not just the happy path.
 */
class Day1ModelTest {

    // =========================================================================
    // CustomerEvent Tests
    // =========================================================================

    @Test
    @DisplayName("CustomerEvent: valid event is created correctly")
    void testValidEventCreation() {
        CustomerEvent event = new CustomerEvent("user_001", EventType.PAGE_VIEW);

        assertEquals("user_001", event.getUserId());
        assertEquals(EventType.PAGE_VIEW, event.getEventType());
        assertNotNull(event.getEventId());      // UUID was generated
        assertNotNull(event.getTimestamp());    // Instant was captured
        assertTrue(event.getMetadata().isEmpty()); // No metadata — empty map, not null
    }

    @Test
    @DisplayName("CustomerEvent: metadata is stored and retrievable")
    void testEventWithMetadata() {
        Map<String, String> meta = Map.of("url", "/pricing", "referrer", "google");
        CustomerEvent event = new CustomerEvent("user_002", EventType.PRICING_VIEW, meta);

        assertEquals("/pricing", event.getMetadata("url"));
        assertEquals("google", event.getMetadata("referrer"));
        assertEquals("", event.getMetadata("nonexistent")); // Missing key → empty string, not null
    }

    @Test
    @DisplayName("CustomerEvent: metadata is immutable — caller cannot modify internals")
    void testMetadataIsImmutable() {
        // This is the DEFENSIVE COPY test — we copy the map in the constructor
        // so the caller mutating their original map doesn't affect our event
        Map<String, String> meta = new java.util.HashMap<>();
        meta.put("url", "/home");
        CustomerEvent event = new CustomerEvent("user_003", EventType.PAGE_VIEW, meta);

        // Caller mutates their map AFTER creating the event
        meta.put("url", "/hacked");

        // Our event should still have the original value
        assertEquals("/home", event.getMetadata("url"),
            "Event metadata was mutated — defensive copy is broken!");
    }

    @Test
    @DisplayName("CustomerEvent: null userId throws IllegalArgumentException")
    void testNullUserIdThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new CustomerEvent(null, EventType.PAGE_VIEW),
            "Should reject null userId");
    }

    @Test
    @DisplayName("CustomerEvent: blank userId throws IllegalArgumentException")
    void testBlankUserIdThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new CustomerEvent("   ", EventType.PAGE_VIEW),
            "Should reject blank userId");
    }

    @Test
    @DisplayName("CustomerEvent: null eventType throws IllegalArgumentException")
    void testNullEventTypeThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new CustomerEvent("user_004", null),
            "Should reject null eventType");
    }

    @Test
    @DisplayName("CustomerEvent: two events have different IDs (UUID uniqueness)")
    void testEventsHaveUniqueIds() {
        CustomerEvent e1 = new CustomerEvent("user_001", EventType.PAGE_VIEW);
        CustomerEvent e2 = new CustomerEvent("user_001", EventType.PAGE_VIEW);

        assertNotEquals(e1.getEventId(), e2.getEventId(),
            "Every event must have a unique ID");
    }

    // =========================================================================
    // UserProfile Tests
    // =========================================================================

    @Test
    @DisplayName("UserProfile: starts with zero counts for all event types")
    void testProfileStartsEmpty() {
        UserProfile profile = new UserProfile("user_001");

        assertEquals(0, profile.getTotalEvents());
        assertEquals(0, profile.getEngagementScore());
        for (EventType type : EventType.values()) {
            assertEquals(0, profile.getEventCount(type),
                "Initial count for " + type + " should be 0");
        }
    }

    @Test
    @DisplayName("UserProfile: recordEvent updates counts correctly")
    void testRecordEventUpdatesCount() {
        UserProfile profile = new UserProfile("user_001");
        CustomerEvent pageView = new CustomerEvent("user_001", EventType.PAGE_VIEW);
        CustomerEvent purchase = new CustomerEvent("user_001", EventType.PURCHASE);

        profile.recordEvent(pageView);
        profile.recordEvent(purchase);
        profile.recordEvent(pageView); // Second page view

        assertEquals(3, profile.getTotalEvents());
        assertEquals(2, profile.getEventCount(EventType.PAGE_VIEW));
        assertEquals(1, profile.getEventCount(EventType.PURCHASE));
    }

    @Test
    @DisplayName("UserProfile: engagement score weighted correctly")
    void testEngagementScoreWeighting() {
        UserProfile profile = new UserProfile("user_001");

        // PURCHASE = weight 50, PAGE_VIEW = weight 1
        profile.recordEvent(new CustomerEvent("user_001", EventType.PURCHASE));
        profile.recordEvent(new CustomerEvent("user_001", EventType.PAGE_VIEW));

        assertEquals(51, profile.getEngagementScore(),
            "Score should be 50 (purchase) + 1 (page view) = 51");
    }

    @Test
    @DisplayName("UserProfile: concurrent updates from multiple threads — no data loss")
    void testConcurrentUpdatesAreThreadSafe() throws InterruptedException {
        // THIS IS THE KEY TEST — proves AtomicInteger works correctly
        UserProfile profile = new UserProfile("user_concurrent");
        int threadCount   = 10;
        int eventsPerThread = 100;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < eventsPerThread; j++) {
                    profile.recordEvent(new CustomerEvent("user_concurrent", EventType.CLICK));
                }
            });
        }

        // Start all threads simultaneously
        for (Thread t : threads) t.start();
        // Wait for all to finish
        for (Thread t : threads) t.join();

        int expected = threadCount * eventsPerThread; // 1000
        assertEquals(expected, profile.getTotalEvents(),
            "With plain int this would be < 1000 due to race conditions. " +
            "AtomicInteger guarantees exactly " + expected);
    }
}

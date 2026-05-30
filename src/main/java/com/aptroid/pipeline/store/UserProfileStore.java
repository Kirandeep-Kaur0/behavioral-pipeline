package com.aptroid.pipeline.store;

import com.aptroid.pipeline.model.EventType;
import com.aptroid.pipeline.model.UserProfile;
import com.aptroid.pipeline.model.UserSegment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Central store for all user profiles.
 * Single source of truth — every consumer reads/writes here.
 *
 * DESIGN DECISIONS (say these in interview):
 *
 * 1. WHY ConcurrentHashMap — not synchronized HashMap?
 *    - synchronized HashMap locks the ENTIRE map for every operation.
 *      Only one thread can read OR write at any time — massive bottleneck.
 *    - ConcurrentHashMap uses segment-level locking internally.
 *      16 threads can update 16 different buckets simultaneously
 *      with zero contention between them.
 *    - At Aptroid's scale — millions of users — this difference
 *      between HashMap and ConcurrentHashMap is the difference
 *      between a system that works and one that crashes under load.
 *
 * 2. WHY computeIfAbsent — not get() + put()?
 *    - get() + put() is TWO operations — not atomic.
 *      Thread A: get("user1") → null
 *      Thread B: get("user1") → null
 *      Thread A: put("user1", new profile)
 *      Thread B: put("user1", new profile) ← overwrites A's profile!
 *      Result: lost events for user1.
 *    - computeIfAbsent() is ONE atomic operation — if two threads
 *      race, exactly one profile gets created. Guaranteed.
 *
 * 3. WHY track totalProfilesCreated separately?
 *    - userProfiles.size() is O(n) on ConcurrentHashMap — it counts
 *      every bucket. Expensive at scale.
 *    - AtomicLong counter is O(1) — just read one value.
 *    - Production systems always use counters, not size() calls.
 */
public class UserProfileStore {

    // The core data structure — userId → UserProfile
    private final ConcurrentHashMap<String, UserProfile> profiles;

    // Stats
    private final AtomicLong totalProfilesCreated = new AtomicLong(0);
    private final AtomicLong totalEventsSeen      = new AtomicLong(0);

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public UserProfileStore() {
        // Initial capacity 256 — avoids resize operations early on
        // Load factor 0.75 — default, good balance of memory vs speed
        // Concurrency level 16 — 16 threads can write simultaneously
        this.profiles = new ConcurrentHashMap<>(256, 0.75f, 16);
    }

    // -------------------------------------------------------------------------
    // Core operations
    // -------------------------------------------------------------------------

    /**
     * Records an event for a user.
     * Creates profile automatically if user is new — atomic.
     * Thread-safe — can be called from any consumer thread.
     */
    public UserProfile recordEvent(com.aptroid.pipeline.model.CustomerEvent event) {
        // computeIfAbsent — atomic get-or-create
        // If profile exists: returns it
        // If not: creates new one AND puts it in map — all in one lock
        UserProfile profile = profiles.computeIfAbsent(
                event.getUserId(),
                userId -> {
                    totalProfilesCreated.incrementAndGet();
                    return new UserProfile(userId);
                }
        );

        // Update profile — safe because UserProfile uses AtomicInteger internally
        profile.recordEvent(event);
        totalEventsSeen.incrementAndGet();

        return profile;
    }

    /**
     * Gets a profile — returns null if user not seen yet.
     */
    public UserProfile getProfile(String userId) {
        return profiles.get(userId);
    }

    /**
     * Updates segment for a user — called by SegmentClassifier.
     */
    public void updateSegment(String userId, UserSegment segment) {
        UserProfile profile = profiles.get(userId);
        if (profile != null) {
            profile.setSegment(segment);
        }
    }

    // -------------------------------------------------------------------------
    // Query methods — used by dashboard and stats
    // -------------------------------------------------------------------------

    /**
     * Returns all profiles sorted by engagement score descending.
     * Most valuable users first.
     */
    public List<UserProfile> getTopUsers(int limit) {
        return profiles.values().stream()
                .sorted(Comparator.comparingInt(UserProfile::getEngagementScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Returns all users in a specific segment.
     */
    public List<UserProfile> getUsersBySegment(UserSegment segment) {
        return profiles.values().stream()
                .filter(p -> p.getSegment() == segment)
                .collect(Collectors.toList());
    }

    /**
     * Returns count of users per segment — for dashboard display.
     */
    public java.util.Map<UserSegment, Long> getSegmentCounts() {
        return profiles.values().stream()
                .collect(Collectors.groupingBy(
                        UserProfile::getSegment,
                        Collectors.counting()
                ));
    }

    /**
     * Returns users who have abandoned cart but never purchased.
     * Classic re-targeting list for marketing campaigns.
     */
    public List<UserProfile> getCartAbandonersNeverPurchased() {
        return profiles.values().stream()
                .filter(p -> p.getEventCount(EventType.CART_ABANDON) > 0
                          && p.getEventCount(EventType.PURCHASE) == 0)
                .collect(Collectors.toList());
    }

    /**
     * Returns high-intent users — viewed pricing but not purchased.
     * Perfect for demo outreach campaigns.
     */
    public List<UserProfile> getHighIntentNotConverted() {
        return profiles.values().stream()
                .filter(p -> p.getEventCount(EventType.PRICING_VIEW) >= 2
                          && p.getEventCount(EventType.PURCHASE) == 0)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Stats + monitoring
    // -------------------------------------------------------------------------

    public int getTotalUsers()              { return profiles.size(); }
    public long getTotalProfilesCreated()   { return totalProfilesCreated.get(); }
    public long getTotalEventsSeen()        { return totalEventsSeen.get(); }

    /**
     * Returns the raw map — used by PipelineThreadPool (backwards compatible).
     */
    public ConcurrentHashMap<String, UserProfile> getRawMap() {
        return profiles;
    }

    public void printSummary() {
        System.out.println("\n  User Profile Store Summary:");
        System.out.println("  Total users tracked:  " + getTotalUsers());
        System.out.println("  Total events seen:    " + getTotalEventsSeen());
        System.out.println("  Profiles created:     " + getTotalProfilesCreated());

        System.out.println("\n  Segment distribution:");
        getSegmentCounts().forEach((segment, count) ->
                System.out.printf("  %-18s -> %d users%n", segment, count));

        List<UserProfile> cartAbandoners = getCartAbandonersNeverPurchased();
        List<UserProfile> highIntent     = getHighIntentNotConverted();

        System.out.println("\n  Actionable lists:");
        System.out.println("  Cart abandoners (no purchase): " + cartAbandoners.size());
        System.out.println("  High intent (no conversion):   " + highIntent.size());
    }
}
package com.aptroid.pipeline.store;

import com.aptroid.pipeline.model.EventType;
import com.aptroid.pipeline.model.UserProfile;
import com.aptroid.pipeline.model.UserSegment;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Classifies users into segments based on their behavior profile.
 * Called after every event — keeps segments always up to date.
 *
 * DESIGN DECISIONS (say these in interview):
 *
 * 1. WHY classify after EVERY event — not in batch?
 *    - Batch classification (run every hour) means stale segments.
 *    - A user who just abandoned cart stays in NEW_USER segment
 *      for an hour — the re-engagement email fires too late.
 *    - Real-time classification means the email fires within
 *      milliseconds of the behavior. That's Aptroid's core value.
 *
 * 2. WHY a priority order for classification?
 *    - A user can match multiple segments simultaneously.
 *      They could be HIGH_VALUE AND have abandoned a cart.
 *    - Priority order: HIGH_VALUE > HOT_LEAD > CART_ABANDONER
 *      > AT_RISK > NEW_USER > UNKNOWN
 *    - Most valuable classification wins — drives best campaign.
 *
 * 3. WHY AT_RISK based on time — not event count?
 *    - A user with 100 events who went silent for 7 days is more
 *      at risk than a new user with 0 events.
 *    - Time-based signals capture churn risk that count-based
 *      signals miss entirely.
 */
public class SegmentClassifier {

    // Thresholds — tunable without changing logic
    private static final int    HIGH_VALUE_SCORE      = 50;
    private static final int    HOT_LEAD_PRICING_VIEWS = 2;
    private static final int    CART_ABANDON_THRESHOLD = 1;
    private static final long   AT_RISK_DAYS           = 2L;
    private static final int    NEW_USER_MAX_EVENTS    = 5;

    private final AtomicLong classificationsRun = new AtomicLong(0);

    // -------------------------------------------------------------------------
    // Core classification
    // -------------------------------------------------------------------------

    /**
     * Classifies a user and updates their segment in-place.
     * Priority order ensures most valuable segment wins.
     */
    public UserSegment classify(UserProfile profile) {
        classificationsRun.incrementAndGet();

        UserSegment segment = determineSegment(profile);
        profile.setSegment(segment);
        return segment;
    }

    private UserSegment determineSegment(UserProfile profile) {

        // ── Priority 1: HIGH_VALUE ────────────────────────────────
        // Purchased OR extremely high engagement score
        if (profile.getEventCount(EventType.PURCHASE) > 0
                || profile.getEngagementScore() >= HIGH_VALUE_SCORE) {
            return UserSegment.HIGH_VALUE;
        }

        // ── Priority 2: HOT_LEAD ──────────────────────────────────
        // Viewed pricing multiple times — strong buying signal
        if (profile.getEventCount(EventType.PRICING_VIEW) >= HOT_LEAD_PRICING_VIEWS) {
            return UserSegment.HOT_LEAD;
        }

        // ── Priority 3: CART_ABANDONER ────────────────────────────
        // Abandoned cart without purchasing
        if (profile.getEventCount(EventType.CART_ABANDON) >= CART_ABANDON_THRESHOLD
                && profile.getEventCount(EventType.PURCHASE) == 0) {
            return UserSegment.CART_ABANDONER;
        }

        // ── Priority 4: AT_RISK ───────────────────────────────────
        // Has history but gone silent for AT_RISK_DAYS days
        if (profile.getTotalEvents() > NEW_USER_MAX_EVENTS
                && isInactive(profile, AT_RISK_DAYS)) {
            return UserSegment.AT_RISK;
        }

        // ── Priority 5: NEW_USER ──────────────────────────────────
        // Few events — still in onboarding window
        if (profile.getTotalEvents() <= NEW_USER_MAX_EVENTS) {
            return UserSegment.NEW_USER;
        }

        // ── Default ───────────────────────────────────────────────
        return UserSegment.UNKNOWN;
    }

    /**
     * Checks if user has been inactive for more than threshold days.
     * Uses volatile lastSeen from UserProfile — always fresh value.
     */
    private boolean isInactive(UserProfile profile, long thresholdDays) {
        Instant cutoff = Instant.now().minus(thresholdDays, ChronoUnit.DAYS);
        return profile.getLastSeen().isBefore(cutoff);
    }

    // -------------------------------------------------------------------------
    // Batch reclassification — run periodically
    // -------------------------------------------------------------------------

    /**
     * Reclassifies ALL users in the store.
     * Called by the dashboard every few seconds to keep segments fresh.
     * Safe to call while pipeline is running — reads are thread-safe.
     */
    public void reclassifyAll(UserProfileStore store) {
        store.getRawMap().values().forEach(this::classify);
    }

    // -------------------------------------------------------------------------
    // Stats
    // -------------------------------------------------------------------------

    public long getClassificationsRun() { return classificationsRun.get(); }
}
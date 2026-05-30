package com.aptroid.pipeline.engine.rules;

import com.aptroid.pipeline.model.EventType;
import com.aptroid.pipeline.model.UserProfile;

/**
 * Fires when a user abandons their cart.
 * Action: Send "You left something behind!" email with discount.
 *
 * APTROID CONNECTION:
 * Cart abandonment emails are one of the highest ROI automations
 * in marketing — typically 15-20% conversion rate.
 * This is exactly what Aptroid's engine triggers automatically.
 */
public class CartAbandonRule implements Rule {

    // Rule fires if user abandoned cart at least this many times
    private final int threshold;

    public CartAbandonRule(int threshold) {
        this.threshold = threshold;
    }

    // Default — fire on first abandon
    public CartAbandonRule() {
        this(1);
    }

    @Override
    public boolean evaluate(UserProfile profile) {
        return profile.getEventCount(EventType.CART_ABANDON) >= threshold;
    }

    @Override
    public String getRuleName() {
        return "CartAbandonRule(threshold=" + threshold + ")";
    }

    @Override
    public int getPriority() {
        return 1; // High priority — cart abandon needs immediate response
    }
}
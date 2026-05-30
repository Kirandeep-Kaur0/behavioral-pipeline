package com.aptroid.pipeline.engine.rules;

import com.aptroid.pipeline.model.EventType;
import com.aptroid.pipeline.model.UserProfile;

/**
 * Fires when a user views the pricing page multiple times.
 * Signal: user is seriously evaluating — high purchase intent.
 * Action: Send "Book a free demo" email.
 *
 * APTROID CONNECTION:
 * Repeated pricing page views are one of the strongest buying
 * signals in B2B marketing. Aptroid tracks exactly this behavior
 * to trigger sales outreach at the perfect moment.
 */
public class PricingPageRule implements Rule {

    private final int viewThreshold;

    public PricingPageRule(int viewThreshold) {
        this.viewThreshold = viewThreshold;
    }

    public PricingPageRule() {
        this(2); // Fire after 2 pricing page views
    }

    @Override
    public boolean evaluate(UserProfile profile) {
        return profile.getEventCount(EventType.PRICING_VIEW) >= viewThreshold;
    }

    @Override
    public String getRuleName() {
        return "PricingPageRule(views>=" + viewThreshold + ")";
    }

    @Override
    public int getPriority() {
        return 2;
    }
}
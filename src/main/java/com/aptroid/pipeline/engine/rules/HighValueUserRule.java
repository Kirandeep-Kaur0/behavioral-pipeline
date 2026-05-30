package com.aptroid.pipeline.engine.rules;

import com.aptroid.pipeline.model.UserProfile;

/**
 * Fires when a user's engagement score crosses a threshold.
 * Signal: highly engaged user — ready for premium offer.
 * Action: Flag as HOT_LEAD, assign to sales team.
 *
 * APTROID CONNECTION:
 * Engagement scoring is core to Aptroid's platform — it combines
 * multiple behavioral signals into one number that drives
 * automated campaign decisions.
 */
public class HighValueUserRule implements Rule {

    private final int scoreThreshold;

    public HighValueUserRule(int scoreThreshold) {
        this.scoreThreshold = scoreThreshold;
    }

    public HighValueUserRule() {
        this(50); // Score above 50 = high value
    }

    @Override
    public boolean evaluate(UserProfile profile) {
        return profile.getEngagementScore() >= scoreThreshold;
    }

    @Override
    public String getRuleName() {
        return "HighValueUserRule(score>=" + scoreThreshold + ")";
    }

    @Override
    public int getPriority() {
        return 3;
    }
}
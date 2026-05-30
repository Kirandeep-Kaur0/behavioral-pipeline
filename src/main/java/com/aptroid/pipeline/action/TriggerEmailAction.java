package com.aptroid.pipeline.action;

import com.aptroid.pipeline.model.UserProfile;
import com.aptroid.pipeline.model.UserSegment;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulates triggering a personalized marketing email.
 * In production this would call SendGrid / Mailchimp / Aptroid's
 * own email delivery API.
 *
 * DESIGN DECISION:
 * AtomicLong emailsSent — multiple consumer threads can trigger
 * emails simultaneously. Plain long++ would lose counts.
 */
public class TriggerEmailAction implements Action {

    private final String emailTemplate;   // Which email to send
    private final String campaignName;    // For analytics tracking
    private final UserSegment targetSegment; // Update user's segment
    private final AtomicLong emailsSent = new AtomicLong(0);

    public TriggerEmailAction(String emailTemplate,
                               String campaignName,
                               UserSegment targetSegment) {
        this.emailTemplate   = emailTemplate;
        this.campaignName    = campaignName;
        this.targetSegment   = targetSegment;
    }

    @Override
    public void execute(UserProfile profile) {
        // Update segment — marks user for this campaign
        profile.setSegment(targetSegment);

        // In production: call email API here
        // EmailAPI.send(profile.getUserId(), emailTemplate, campaignName);

        emailsSent.incrementAndGet();

        System.out.printf(
            "  [EMAIL TRIGGERED] user=%-12s | template=%-30s | campaign=%s | time=%s%n",
            profile.getUserId(),
            emailTemplate,
            campaignName,
            Instant.now()
        );
    }

    @Override
    public String getActionName() {
        return "TriggerEmailAction(" + emailTemplate + ")";
    }

    public long getEmailsSent() { return emailsSent.get(); }
}
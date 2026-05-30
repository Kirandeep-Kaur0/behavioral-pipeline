package com.aptroid.pipeline.action;

import com.aptroid.pipeline.model.UserProfile;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Logs rule firing to console / audit trail.
 * In production: writes to database, sends to Elasticsearch,
 * or pushes to a monitoring system like Datadog.
 */
public class LogAction implements Action {

    private final String logMessage;
    private final AtomicLong logCount = new AtomicLong(0);

    public LogAction(String logMessage) {
        this.logMessage = logMessage;
    }

    @Override
    public void execute(UserProfile profile) {
        logCount.incrementAndGet();
        System.out.printf(
            "  [AUDIT LOG]      user=%-12s | event=%s | score=%d | time=%s%n",
            profile.getUserId(), logMessage,
            profile.getEngagementScore(), Instant.now()
        );
    }

    @Override
    public String getActionName() {
        return "LogAction(" + logMessage + ")";
    }

    public long getLogCount() { return logCount.get(); }
}
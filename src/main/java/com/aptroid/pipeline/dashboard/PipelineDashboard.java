package com.aptroid.pipeline.dashboard;

import com.aptroid.pipeline.engine.RuleEngine;
import com.aptroid.pipeline.queue.EventQueue;
import com.aptroid.pipeline.store.SegmentClassifier;
import com.aptroid.pipeline.store.UserProfileStore;
import com.aptroid.pipeline.model.UserProfile;
import com.aptroid.pipeline.model.UserSegment;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Live CLI dashboard — refreshes every 2 seconds while pipeline runs.
 * This is what the interviewer SEES on your screen during the demo.
 *
 * DESIGN DECISIONS (say these in interview):
 *
 * 1. WHY ScheduledExecutorService — not Thread.sleep() in a loop?
 *    - Thread.sleep() loop drifts over time. If refresh takes 50ms,
 *      next refresh is at 2050ms, then 2100ms — drift accumulates.
 *    - ScheduledExecutorService.scheduleAtFixedRate() fires at
 *      exactly 2000ms intervals regardless of how long the task takes.
 *    - Production monitoring systems (Datadog, Grafana) use fixed-rate
 *      scheduling for exactly this reason.
 *
 * 2. WHY track previousEnqueued for throughput calculation?
 *    - Throughput = events THIS second, not total events ever.
 *    - We snapshot the counter, subtract the previous snapshot,
 *      divide by interval. This gives events/sec right now.
 *    - Same technique used in OS network monitoring (bytes/sec).
 *
 * 3. WHY clear screen with ANSI escape codes?
 *    - \033[H\033[2J moves cursor to top-left and clears screen.
 *    - Dashboard rewrites the same screen area every refresh —
 *      looks like a live updating display, not scrolling text.
 *    - This is how htop, top, and production CLI dashboards work.
 */
public class PipelineDashboard {

    private final EventQueue        eventQueue;
    private final UserProfileStore  profileStore;
    private final RuleEngine        ruleEngine;
    private final SegmentClassifier classifier;

    // For throughput calculation — events/sec
    private final AtomicLong previousEnqueued = new AtomicLong(0);
    private final AtomicLong previousConsumed = new AtomicLong(0);

    // Track peak throughput seen so far
    private long peakThroughput = 0;

    // Total consumed — updated by dashboard from thread pool stats
    private long totalConsumed = 0;

    private final ScheduledExecutorService scheduler;
    private final DateTimeFormatter timeFormatter =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final int REFRESH_SECONDS = 2;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public PipelineDashboard(EventQueue eventQueue,
                              UserProfileStore profileStore,
                              RuleEngine ruleEngine,
                              SegmentClassifier classifier) {
        this.eventQueue   = eventQueue;
        this.profileStore = profileStore;
        this.ruleEngine   = ruleEngine;
        this.classifier   = classifier;

        // Single daemon thread — dies automatically when main thread exits
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "dashboard-thread");
            t.setDaemon(true); // Daemon — won't prevent JVM shutdown
            return t;
        });
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Starts the dashboard — refreshes every REFRESH_SECONDS seconds.
     * Non-blocking — returns immediately, dashboard runs in background.
     */
    public void start() {
        scheduler.scheduleAtFixedRate(
            this::render,           // What to run
            0,                      // Initial delay — start immediately
            REFRESH_SECONDS,        // Period
            TimeUnit.SECONDS
        );
    }

    /**
     * Stops the dashboard gracefully.
     */
    public void stop() {
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void setTotalConsumed(long consumed) {
        this.totalConsumed = consumed;
    }

    // -------------------------------------------------------------------------
    // Core render method — called every 2 seconds
    // -------------------------------------------------------------------------

    private void render() {
        try {
            // Reclassify all users before rendering — keeps segments fresh
            classifier.reclassifyAll(profileStore);

            // Calculate throughput since last render
            long currentEnqueued = eventQueue.getTotalEnqueued();
            long currentConsumed = totalConsumed;

            long enqueuedDelta = currentEnqueued - previousEnqueued.get();
            long consumedDelta = currentConsumed - previousConsumed.get();

            long throughputPerSec = enqueuedDelta / REFRESH_SECONDS;
            if (throughputPerSec > peakThroughput) {
                peakThroughput = throughputPerSec;
            }

            previousEnqueued.set(currentEnqueued);
            previousConsumed.set(currentConsumed);

            // Build the dashboard string
            StringBuilder sb = new StringBuilder();

            // Clear screen — ANSI escape code
            sb.append("\033[H\033[2J");

            // ── Header ────────────────────────────────────────────
            sb.append(box("=", 62)).append("\n");
            sb.append(center(
                "REAL-TIME BEHAVIORAL EVENT PIPELINE — LIVE DASHBOARD", 62))
              .append("\n");
            sb.append(center(
                "Time: " + LocalTime.now().format(timeFormatter), 62))
              .append("\n");
            sb.append(box("=", 62)).append("\n\n");

            // ── Throughput ────────────────────────────────────────
            sb.append("  THROUGHPUT\n");
            sb.append(box("-", 62)).append("\n");
            sb.append(String.format(
                "  Events/sec (current):  %-8d  Peak: %d%n",
                throughputPerSec, peakThroughput));
            sb.append(String.format(
                "  Total enqueued:        %-8d%n", currentEnqueued));
            sb.append(String.format(
                "  Total consumed:        %-8d%n", currentConsumed));
            sb.append(String.format(
                "  Total dropped:         %-8d%n", eventQueue.getTotalDropped()));
            sb.append(String.format(
                "  Queue depth:           %d / %d  (%.1f%% full)%n",
                eventQueue.getCurrentSize(),
                eventQueue.getCapacity(),
                eventQueue.getFillPercent()));
            sb.append("\n");

            // ── User segments ─────────────────────────────────────
            sb.append("  USER SEGMENTS  (").append(profileStore.getTotalUsers())
              .append(" users tracked)\n");
            sb.append(box("-", 62)).append("\n");

            Map<UserSegment, Long> segmentCounts = profileStore.getSegmentCounts();
            for (UserSegment seg : UserSegment.values()) {
                long count = segmentCounts.getOrDefault(seg, 0L);
                if (count > 0) {
                    sb.append(String.format(
                        "  %-18s  %s  %d%n",
                        seg,
                        bar(count, profileStore.getTotalUsers(), 20),
                        count));
                }
            }
            sb.append("\n");

            // ── Rule engine ───────────────────────────────────────
            sb.append("  RULE ENGINE\n");
            sb.append(box("-", 62)).append("\n");
            sb.append(String.format(
                "  Rules fired total:     %d%n",
                ruleEngine.getTotalRulesFired()));
            sb.append(String.format(
                "  Actions executed:      %d%n",
                ruleEngine.getTotalActionsRun()));
            sb.append(String.format(
                "  Classifications run:   %d%n",
                classifier.getClassificationsRun()));
            sb.append("\n");

            // ── Top 5 users ───────────────────────────────────────
            sb.append("  TOP 5 USERS BY ENGAGEMENT\n");
            sb.append(box("-", 62)).append("\n");
            List<UserProfile> topUsers = profileStore.getTopUsers(5);
            for (UserProfile p : topUsers) {
                sb.append(String.format(
                    "  %-14s  score=%-5d  events=%-4d  %s%n",
                    p.getUserId(),
                    p.getEngagementScore(),
                    p.getTotalEvents(),
                    segmentBadge(p.getSegment())));
            }
            sb.append("\n");

            // ── Actionable lists ──────────────────────────────────
            int cartAbandoners = profileStore.getCartAbandonersNeverPurchased().size();
            int highIntent     = profileStore.getHighIntentNotConverted().size();

            sb.append("  ACTIONABLE MARKETING LISTS\n");
            sb.append(box("-", 62)).append("\n");
            sb.append(String.format(
                "  Cart abandoners (→ discount email):    %d users%n",
                cartAbandoners));
            sb.append(String.format(
                "  High intent, not converted (→ demo):   %d users%n",
                highIntent));
            sb.append("\n");

            // ── Footer ────────────────────────────────────────────
            sb.append(box("-", 62)).append("\n");
            sb.append("  Refreshing every ")
              .append(REFRESH_SECONDS)
              .append("s  |  Press Ctrl+C to stop\n");
            sb.append(box("=", 62)).append("\n");

            // Print everything at once — avoids partial renders
            System.out.print(sb);

        } catch (Exception e) {
            // Never crash the dashboard thread — log and continue
            System.err.println("[Dashboard] Render error: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Rendering helpers
    // -------------------------------------------------------------------------

    /** ASCII progress bar: ████████░░░░  */
    private String bar(long value, int total, int width) {
        if (total == 0) return "░".repeat(width);
        int filled = (int) Math.round((double) value / total * width);
        filled = Math.min(filled, width);
        return "█".repeat(filled) + "░".repeat(width - filled);
    }

    /** Segment badge with label */
    private String segmentBadge(UserSegment segment) {
        return switch (segment) {
            case HIGH_VALUE     -> "[HIGH_VALUE  ]";
            case HOT_LEAD       -> "[HOT_LEAD    ]";
            case CART_ABANDONER -> "[CART_ABANDON]";
            case AT_RISK        -> "[AT_RISK     ]";
            case NEW_USER       -> "[NEW_USER    ]";
            default             -> "[UNKNOWN     ]";
        };
    }

    private String box(String ch, int width) {
        return "  " + ch.repeat(width);
    }

    private String center(String text, int width) {
        int padding = Math.max(0, (width - text.length()) / 2);
        return " ".repeat(padding) + text;
    }
}
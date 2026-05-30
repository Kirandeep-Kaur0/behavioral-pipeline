package com.aptroid.pipeline;

import com.aptroid.pipeline.action.LogAction;
import com.aptroid.pipeline.action.TriggerEmailAction;
import com.aptroid.pipeline.dashboard.PipelineDashboard;
import com.aptroid.pipeline.engine.PipelineThreadPool;
import com.aptroid.pipeline.engine.RuleEngine;
import com.aptroid.pipeline.engine.rules.CartAbandonRule;
import com.aptroid.pipeline.engine.rules.HighValueUserRule;
import com.aptroid.pipeline.engine.rules.PricingPageRule;
import com.aptroid.pipeline.model.UserSegment;
import com.aptroid.pipeline.queue.EventProducer;
import com.aptroid.pipeline.queue.EventQueue;
import com.aptroid.pipeline.store.SegmentClassifier;
import com.aptroid.pipeline.store.UserProfileStore;

import java.util.List;

public class Main {

    public static void main(String[] args) throws InterruptedException {

        // ── Step 1: Rule engine ───────────────────────────────────
        RuleEngine ruleEngine = new RuleEngine();

        ruleEngine.registerRule(
            new CartAbandonRule(1),
            List.of(
                new TriggerEmailAction(
                    "cart_abandon_discount",
                    "cart-recovery",
                    UserSegment.CART_ABANDONER),
                new LogAction("CART_ABANDON")
            )
        );
        ruleEngine.registerRule(
            new PricingPageRule(2),
            List.of(
                new TriggerEmailAction(
                    "book_free_demo",
                    "demo-campaign",
                    UserSegment.HOT_LEAD),
                new LogAction("HOT_LEAD")
            )
        );
        ruleEngine.registerRule(
            new HighValueUserRule(50),
            List.of(
                new TriggerEmailAction(
                    "premium_upgrade",
                    "high-value-campaign",
                    UserSegment.HIGH_VALUE),
                new LogAction("HIGH_VALUE")
            )
        );

        // ── Step 2: Store + classifier ────────────────────────────
        UserProfileStore profileStore = new UserProfileStore();
        SegmentClassifier classifier  = new SegmentClassifier();

        // ── Step 3: Queue + producers ─────────────────────────────
        EventQueue queue = new EventQueue(500);

        String[] users = {
            "user_alice", "user_bob",   "user_carol",
            "user_dave",  "user_eve",   "user_frank",
            "user_grace", "user_henry", "user_iris"
        };

        EventProducer p1 = new EventProducer("Producer-1", queue, users, 10);
        EventProducer p2 = new EventProducer("Producer-2", queue, users, 8);
        EventProducer p3 = new EventProducer("Producer-3", queue, users, 5);

        Thread t1 = new Thread(p1, "producer-thread-1");
        Thread t2 = new Thread(p2, "producer-thread-2");
        Thread t3 = new Thread(p3, "producer-thread-3");

        // ── Step 4: Thread pool ───────────────────────────────────
        PipelineThreadPool threadPool = new PipelineThreadPool(
                3, queue, profileStore, classifier, ruleEngine);

        // ── Step 5: Dashboard ─────────────────────────────────────
        PipelineDashboard dashboard = new PipelineDashboard(
                queue, profileStore, ruleEngine, classifier);

        // ── Step 6: Start everything ──────────────────────────────
        System.out.println("Starting pipeline...");
        threadPool.start();
        Thread.sleep(200);

        t1.start(); t2.start(); t3.start();
        Thread.sleep(200);

        // Start dashboard AFTER producers are running
        dashboard.start();

        // ── Step 7: Keep updating dashboard with consumed count ───
        // Run for 15 seconds — long enough to see dashboard refresh
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 60_000) {
            dashboard.setTotalConsumed(threadPool.getTotalProcessed());
            Thread.sleep(500); // Update consumed count every 500ms
        }

        // ── Step 8: Shutdown ──────────────────────────────────────
        dashboard.stop();

        p1.stop(); p2.stop(); p3.stop();
        t1.join(); t2.join(); t3.join();

        classifier.reclassifyAll(profileStore);
        threadPool.shutdown();

        // ── Step 9: Final summary ─────────────────────────────────
        System.out.println("\n\n" + "=".repeat(62));
        System.out.println("  PIPELINE COMPLETE — FINAL REPORT");
        System.out.println("=".repeat(62));

        System.out.printf("  Runtime:         60 seconds%n");
        System.out.printf("  Total enqueued:  %d%n", queue.getTotalEnqueued());
        System.out.printf("  Total consumed:  %d%n", threadPool.getTotalProcessed());
        System.out.printf("  Avg throughput:  %d events/sec%n",
                queue.getTotalEnqueued() / 15);
        System.out.printf("  Unique users:    %d%n", profileStore.getTotalUsers());

        profileStore.printSummary();
        ruleEngine.printStats();

        System.out.println("=".repeat(62));
    }
}
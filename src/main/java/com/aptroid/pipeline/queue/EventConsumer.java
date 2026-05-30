package com.aptroid.pipeline.queue;

import com.aptroid.pipeline.engine.RuleEngine;
import com.aptroid.pipeline.model.CustomerEvent;
import com.aptroid.pipeline.model.UserProfile;
import com.aptroid.pipeline.store.SegmentClassifier;
import com.aptroid.pipeline.store.UserProfileStore;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Updated Day 5 — uses UserProfileStore and SegmentClassifier.
 * Processing flow per event:
 * 1. Record event in UserProfileStore
 * 2. Classify user segment (real-time, after every event)
 * 3. Run rule engine against updated profile
 */
public class EventConsumer implements Runnable {

    private final String consumerId;
    private final EventQueue eventQueue;
    private final UserProfileStore profileStore;      // Day 5 upgrade
    private final SegmentClassifier classifier;       // Day 5 upgrade
    private final RuleEngine ruleEngine;

    private final AtomicBoolean running;
    private final AtomicLong eventsProcessed;
    private final AtomicLong totalLatencyMs;

    public EventConsumer(String consumerId,
                         EventQueue eventQueue,
                         UserProfileStore profileStore,
                         SegmentClassifier classifier,
                         RuleEngine ruleEngine) {
        this.consumerId    = consumerId;
        this.eventQueue    = eventQueue;
        this.profileStore  = profileStore;
        this.classifier    = classifier;
        this.ruleEngine    = ruleEngine;
        this.running       = new AtomicBoolean(true);
        this.eventsProcessed = new AtomicLong(0);
        this.totalLatencyMs  = new AtomicLong(0);
    }

    @Override
    public void run() {
        System.out.println("[" + consumerId + "] Started on thread: "
                + Thread.currentThread().getName());

        while (running.get()) {
            CustomerEvent event = eventQueue.dequeue();
            if (event == null) continue;

            long latencyMs = System.currentTimeMillis()
                    - event.getTimestamp().toEpochMilli();
            totalLatencyMs.addAndGet(Math.max(0, latencyMs));

            processEvent(event);
            eventsProcessed.incrementAndGet();
        }

        System.out.println("[" + consumerId + "] Stopped — processed "
                + eventsProcessed.get() + " events");
    }

    private void processEvent(CustomerEvent event) {
        // Step 1 — record event, get updated profile
        UserProfile profile = profileStore.recordEvent(event);

        // Step 2 — reclassify segment in real time
        classifier.classify(profile);

        // Step 3 — fire rules against updated profile
        ruleEngine.evaluate(profile);
    }

    public void stop()               { running.set(false); }
    public long getEventsProcessed() { return eventsProcessed.get(); }
    public String getConsumerId()    { return consumerId; }
    public double getAvgLatencyMs() {
        long p = eventsProcessed.get();
        return p == 0 ? 0.0 : (double) totalLatencyMs.get() / p;
    }
}
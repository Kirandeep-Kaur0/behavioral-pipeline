package com.aptroid.pipeline.queue;

import com.aptroid.pipeline.model.CustomerEvent;
import com.aptroid.pipeline.model.EventType;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulates customers generating real-time behavioral events.
 * Multiple EventProducers run simultaneously on separate threads.
 *
 * DESIGN DECISIONS (say these in interview):
 *
 * 1. WHY implements Runnable, not extends Thread?
 *    - Implementing Runnable separates WHAT to do from HOW to run it.
 *    - Runnable can be submitted to any Executor — flexible.
 *    - Java only allows single inheritance — extending Thread blocks
 *      you from extending anything else.
 *
 * 2. WHY AtomicBoolean for the running flag — not boolean?
 *    - Main thread writes running=false to stop the producer.
 *    - Without volatile/Atomic, JVM can cache the old value in a
 *      CPU register — producer never sees the update, loops forever.
 *    - AtomicBoolean guarantees visibility across threads.
 *
 * 3. WHY weighted random events?
 *    - Real user behavior is not uniform. PAGE_VIEW happens 10x
 *      more than PURCHASE. Weighted simulation produces realistic
 *      data that makes the rule engine meaningful.
 */
public class EventProducer implements Runnable {

    private final String producerId;
    private final EventQueue eventQueue;
    private final int eventsPerSecond;
    private final AtomicBoolean running;
    private final AtomicLong eventCount;
    private final String[] userPool;

    private static final EventType[] WEIGHTED_EVENTS = {
        EventType.PAGE_VIEW,   EventType.PAGE_VIEW,   EventType.PAGE_VIEW,
        EventType.PAGE_VIEW,   EventType.PAGE_VIEW,
        EventType.CLICK,       EventType.CLICK,        EventType.CLICK,
        EventType.PRICING_VIEW,
        EventType.CART_ADD,
        EventType.CART_ABANDON,
        EventType.EMAIL_OPEN,
        EventType.LOGIN,
        EventType.PURCHASE
    };

    private final Random random = new Random();

    public EventProducer(String producerId, EventQueue queue,
                         String[] userPool, int eventsPerSecond) {
        this.producerId      = producerId;
        this.eventQueue      = queue;
        this.userPool        = userPool;
        this.eventsPerSecond = eventsPerSecond;
        this.running         = new AtomicBoolean(true);
        this.eventCount      = new AtomicLong(0);
    }

    @Override
    public void run() {
        System.out.println("[" + producerId + "] Started — targeting "
                + eventsPerSecond + " events/sec");

        long sleepMs = 1000L / eventsPerSecond;

        while (running.get()) {
            try {
                String userId        = userPool[random.nextInt(userPool.length)];
                EventType eventType  = WEIGHTED_EVENTS[random.nextInt(WEIGHTED_EVENTS.length)];
                Map<String, String> metadata = buildMetadata(eventType);
                CustomerEvent event  = new CustomerEvent(userId, eventType, metadata);

                boolean accepted = eventQueue.enqueue(event);
                if (accepted) {
                    eventCount.incrementAndGet();
                }

                Thread.sleep(sleepMs);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("[" + producerId + "] Stopped — sent "
                + eventCount.get() + " events total");
    }

    private Map<String, String> buildMetadata(EventType type) {
        return switch (type) {
            case PAGE_VIEW    -> Map.of("url", randomPage(), "referrer", randomReferrer());
            case PRICING_VIEW -> Map.of("url", "/pricing", "plan", randomPlan());
            case CLICK        -> Map.of("button", randomButton());
            case CART_ADD     -> Map.of("product", randomProduct(), "amount", randomAmount());
            case CART_ABANDON -> Map.of("items_left", String.valueOf(random.nextInt(5) + 1));
            case PURCHASE     -> Map.of("amount", randomAmount(), "product", randomProduct());
            case EMAIL_OPEN   -> Map.of("campaign", "welcome_series");
            default           -> Map.of();
        };
    }

    private String randomPage()     {
        String[] p = {"/home", "/features", "/about", "/blog", "/docs"};
        return p[random.nextInt(p.length)];
    }
    private String randomReferrer() {
        String[] r = {"google", "direct", "twitter", "email", "linkedin"};
        return r[random.nextInt(r.length)];
    }
    private String randomPlan()     {
        String[] p = {"starter", "pro", "enterprise"};
        return p[random.nextInt(p.length)];
    }
    private String randomButton()   {
        String[] b = {"sign_up", "learn_more", "get_started", "watch_demo"};
        return b[random.nextInt(b.length)];
    }
    private String randomProduct()  {
        String[] p = {"PRO_PLAN", "STARTER_PLAN", "ENTERPRISE_PLAN", "ADD_ON_ANALYTICS"};
        return p[random.nextInt(p.length)];
    }
    private String randomAmount()   {
        int[] a = {99, 199, 299, 499, 999};
        return String.valueOf(a[random.nextInt(a.length)]);
    }

    public void stop()              { running.set(false); }
    public long getEventCount()     { return eventCount.get(); }
    public String getProducerId()   { return producerId; }
}
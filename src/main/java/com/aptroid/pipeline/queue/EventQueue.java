package com.aptroid.pipeline.queue;

import com.aptroid.pipeline.model.CustomerEvent;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The central event queue — the entry point of the entire pipeline.
 * All producers push here. All consumers pull from here.
 *
 * DESIGN DECISIONS (say these in interview):
 *
 * 1. WHY LinkedBlockingQueue over ArrayList + synchronized?
 *    - BlockingQueue handles back-pressure automatically.
 *      When the queue is full, producers BLOCK — they don't crash,
 *      they don't drop events, they just wait. No extra code needed.
 *    - When the queue is empty, consumers BLOCK instead of
 *      busy-spinning (wasting CPU checking "is there anything yet?")
 *    - Thread-safe by design — no synchronized keyword needed anywhere.
 *
 * 2. WHY a capacity limit (1000 events)?
 *    - Unbounded queues are dangerous. If consumers fall behind,
 *      the queue grows forever → OutOfMemoryError → crash.
 *    - A bounded queue applies back-pressure: producers slow down
 *      automatically when consumers can't keep up. This is how
 *      production systems like Kafka work.
 *
 * 3. WHY AtomicLong for stats — not long?
 *    - Multiple producer threads call enqueue() simultaneously.
 *    - Plain long++ is NOT atomic (read-modify-write = 3 ops).
 *    - AtomicLong.incrementAndGet() = 1 atomic CPU instruction.
 */
public class EventQueue {

    private final LinkedBlockingQueue<CustomerEvent> queue;

    private final AtomicLong totalEnqueued = new AtomicLong(0);
    private final AtomicLong totalDequeued = new AtomicLong(0);
    private final AtomicLong totalDropped  = new AtomicLong(0);

    private final int capacity;

    public EventQueue(int capacity) {
        this.capacity = capacity;
        this.queue    = new LinkedBlockingQueue<>(capacity);
    }

    public EventQueue() {
        this(1000);
    }

    public boolean enqueue(CustomerEvent event) {
        try {
            boolean accepted = queue.offer(event, 100, TimeUnit.MILLISECONDS);
            if (accepted) {
                totalEnqueued.incrementAndGet();
            } else {
                totalDropped.incrementAndGet();
                System.err.println("[WARN] Queue full — event dropped: "
                        + event.getEventType() + " for user " + event.getUserId());
            }
            return accepted;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public CustomerEvent dequeue() {
        try {
            CustomerEvent event = queue.poll(1, TimeUnit.SECONDS);
            if (event != null) {
                totalDequeued.incrementAndGet();
            }
            return event;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public int getCurrentSize()     { return queue.size(); }
    public int getCapacity()        { return capacity; }
    public long getTotalEnqueued()  { return totalEnqueued.get(); }
    public long getTotalDequeued()  { return totalDequeued.get(); }
    public long getTotalDropped()   { return totalDropped.get(); }
    public double getFillPercent()  { return (queue.size() * 100.0) / capacity; }
    public boolean isEmpty()        { return queue.isEmpty(); }

    @Override
    public String toString() {
        return String.format(
            "EventQueue{size=%d/%d (%.1f%%), enqueued=%d, dequeued=%d, dropped=%d}",
            getCurrentSize(), capacity, getFillPercent(),
            totalEnqueued.get(), totalDequeued.get(), totalDropped.get()
        );
    }
}
package com.aptroid.pipeline.engine;

import com.aptroid.pipeline.queue.EventConsumer;
import com.aptroid.pipeline.queue.EventQueue;
import com.aptroid.pipeline.store.SegmentClassifier;
import com.aptroid.pipeline.store.UserProfileStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Updated Day 5 — injects UserProfileStore and SegmentClassifier
 * into each consumer worker.
 */
public class PipelineThreadPool {

    private final ThreadPoolExecutor executor;
    private final List<EventConsumer> consumers;
    private final int threadCount;

    public PipelineThreadPool(int threadCount,
                               EventQueue eventQueue,
                               UserProfileStore profileStore,
                               SegmentClassifier classifier,
                               RuleEngine ruleEngine) {
        this.threadCount = threadCount;
        this.consumers   = new ArrayList<>();

        AtomicInteger threadNumber = new AtomicInteger(1);
        ThreadFactory namedThreadFactory = runnable -> {
            Thread t = new Thread(runnable);
            t.setName("consumer-worker-" + threadNumber.getAndIncrement());
            t.setDaemon(false);
            return t;
        };

        this.executor = new ThreadPoolExecutor(
            threadCount, threadCount,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(50),
            namedThreadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy()
        );

        for (int i = 1; i <= threadCount; i++) {
            consumers.add(new EventConsumer(
                "Consumer-" + i,
                eventQueue,
                profileStore,
                classifier,
                ruleEngine
            ));
        }
    }

    public void start() {
        System.out.println("[ThreadPool] Starting " + threadCount
                + " consumer threads...");
        consumers.forEach(executor::submit);
        System.out.println("[ThreadPool] All consumers running.");
    }

    public void shutdown() {
        System.out.println("\n[ThreadPool] Initiating graceful shutdown...");
        consumers.forEach(EventConsumer::stop);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS))
                executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("[ThreadPool] Shutdown complete.");
    }

    public long getTotalProcessed() {
        return consumers.stream()
                .mapToLong(EventConsumer::getEventsProcessed).sum();
    }

    public double getAvgLatencyMs() {
        return consumers.stream()
                .mapToDouble(EventConsumer::getAvgLatencyMs)
                .average().orElse(0.0);
    }

    public void printStats() {
        System.out.println("\n  Thread Pool internals:");
        System.out.println("  Active threads:  " + executor.getActiveCount());
        System.out.println("  Completed tasks: " + executor.getCompletedTaskCount());
        System.out.printf( "  Avg latency:     %.2f ms%n", getAvgLatencyMs());
        System.out.println("\n  Per-consumer breakdown:");
        for (EventConsumer c : consumers) {
            System.out.printf("  %-14s → processed=%-5d | avg_latency=%.2f ms%n",
                    c.getConsumerId(),
                    c.getEventsProcessed(),
                    c.getAvgLatencyMs());
        }
    }
}
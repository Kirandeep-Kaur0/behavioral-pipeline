package com.aptroid.pipeline.engine;

import com.aptroid.pipeline.action.Action;
import com.aptroid.pipeline.engine.rules.Rule;
import com.aptroid.pipeline.model.UserProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Evaluates all rules against a user profile and fires actions.
 *
 * DESIGN DECISIONS (say these in interview):
 *
 * 1. WHY sorted by priority?
 *    Rules have priority numbers. Lower = evaluated first.
 *    Cart abandon (priority 1) fires before high-value check (priority 3).
 *    This mirrors production rule engines that need ordered evaluation.
 *
 * 2. WHY ConcurrentHashMap for firedRules tracking?
 *    Multiple consumer threads call evaluate() simultaneously.
 *    We track which rules already fired per user to avoid
 *    sending the same email 50 times. ConcurrentHashMap makes
 *    this tracking thread-safe.
 *
 * 3. WHY List<Action> per rule — not single Action?
 *    One rule can trigger multiple actions:
 *    CartAbandon → send email AND log to audit AND update CRM.
 *    Decoupled design — rules don't know what actions do.
 */
public class RuleEngine {

    // Each rule maps to a list of actions it triggers
    private final List<RuleActionPair> ruleActionPairs = new ArrayList<>();

    // Tracks which rules already fired per user — prevents duplicate emails
    // Key: userId + ruleName, Value: timestamp when it fired
    private final ConcurrentHashMap<String, Long> firedRules = new ConcurrentHashMap<>();

    // Global stats
    private final AtomicLong totalRulesFired   = new AtomicLong(0);
    private final AtomicLong totalActionsRun   = new AtomicLong(0);

    // Cooldown — don't fire same rule for same user within 10 seconds
    // In production this would be hours/days
    private static final long COOLDOWN_MS = 10_000;

    // -------------------------------------------------------------------------
    // Registration — wire rules to actions
    // -------------------------------------------------------------------------

    /**
     * Register a rule with one or more actions.
     * Called once at startup to configure the engine.
     */
    public void registerRule(Rule rule, List<Action> actions) {
        ruleActionPairs.add(new RuleActionPair(rule, actions));
        // Keep sorted by priority — lower number fires first
        ruleActionPairs.sort(Comparator.comparingInt(p -> p.rule.getPriority()));
    }

    // -------------------------------------------------------------------------
    // Core evaluation — called by EventConsumer on every event
    // -------------------------------------------------------------------------

    /**
     * Evaluates ALL rules against the user's current profile.
     * Fires actions for any rule that matches.
     * Thread-safe — multiple consumers call this simultaneously.
     */
    public void evaluate(UserProfile profile) {

        for (RuleActionPair pair : ruleActionPairs) {

            // Check cooldown — has this rule fired recently for this user?
            String cooldownKey = profile.getUserId() + ":" + pair.rule.getRuleName();
            long now           = System.currentTimeMillis();
            Long lastFired     = firedRules.get(cooldownKey);

            if (lastFired != null && (now - lastFired) < COOLDOWN_MS) {
                continue; // Still in cooldown — skip
            }

            // Evaluate the rule
            if (pair.rule.evaluate(profile)) {

                // Record firing time — ConcurrentHashMap put is thread-safe
                firedRules.put(cooldownKey, now);
                totalRulesFired.incrementAndGet();

                // Fire all actions for this rule
                for (Action action : pair.actions) {
                    action.execute(profile);
                    totalActionsRun.incrementAndGet();
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Stats
    // -------------------------------------------------------------------------

    public long getTotalRulesFired() { return totalRulesFired.get(); }
    public long getTotalActionsRun() { return totalActionsRun.get(); }

    public void printStats() {
        System.out.println("\n  Rule Engine Stats:");
        System.out.println("  Rules registered: " + ruleActionPairs.size());
        System.out.println("  Total fired:      " + totalRulesFired.get());
        System.out.println("  Total actions:    " + totalActionsRun.get());
        System.out.println("  Cooldown entries: " + firedRules.size());
        System.out.println("\n  Rules configured:");
        for (RuleActionPair pair : ruleActionPairs) {
            System.out.println("  [P" + pair.rule.getPriority() + "] "
                + pair.rule.getRuleName() + " -> "
                + pair.actions.stream()
                    .map(Action::getActionName)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("none"));
        }
    }

    // -------------------------------------------------------------------------
    // Inner class — pairs a rule with its actions
    // -------------------------------------------------------------------------

    private static class RuleActionPair {
        final Rule rule;
        final List<Action> actions;

        RuleActionPair(Rule rule, List<Action> actions) {
            this.rule    = rule;
            this.actions = new ArrayList<>(actions);
        }
    }
}
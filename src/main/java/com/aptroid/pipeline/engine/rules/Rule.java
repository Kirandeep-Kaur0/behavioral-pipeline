package com.aptroid.pipeline.engine.rules;

import com.aptroid.pipeline.model.UserProfile;

/**
 * The Rule interface — Strategy Pattern.
 *
 * DESIGN DECISION (say this in interview):
 * Strategy Pattern means each rule is a completely separate class
 * implementing one interface. The RuleEngine doesn't care what
 * the rule does — it just calls evaluate().
 *
 * Adding a NEW rule = create one new class, zero changes anywhere else.
 * This is the Open/Closed Principle — open for extension, closed
 * for modification. Production rule engines at companies like
 * Aptroid have hundreds of rules added this way.
 */
public interface Rule {

    /**
     * Evaluates whether this rule fires for the given user profile.
     * @return true if rule condition is met and action should trigger
     */
    boolean evaluate(UserProfile profile);

    /**
     * Human-readable name — shown in logs and dashboard.
     */
    String getRuleName();

    /**
     * Priority — lower number = evaluated first.
     * High priority rules (like PURCHASE) fire before low priority ones.
     */
    int getPriority();
}
package com.aptroid.pipeline.action;

import com.aptroid.pipeline.model.UserProfile;

/**
 * The Action interface — Observer Pattern.
 *
 * DESIGN DECISION (say this in interview):
 * When a Rule fires, it notifies its Action to execute.
 * This is the Observer pattern — the rule engine is the subject,
 * actions are the observers.
 *
 * Actions are completely decoupled from rules:
 * - CartAbandonRule can trigger TriggerEmailAction
 * - Same CartAbandonRule could also trigger LogAction
 * - One rule can have MULTIPLE actions
 * In production: actions call real email APIs, SMS gateways,
 * Slack webhooks, CRM updates — all wired here.
 */
public interface Action {

    /**
     * Executes the action for the given user profile.
     */
    void execute(UserProfile profile);

    /**
     * Human-readable description for logs.
     */
    String getActionName();
}
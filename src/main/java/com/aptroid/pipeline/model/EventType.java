package com.aptroid.pipeline.model;

/**
 * All possible customer behavior events tracked by the pipeline.
 *
 * WHY AN ENUM?
 * - Type-safe: impossible to pass an invalid event type as a String
 * - Switch-friendly: compiler warns if you miss a case
 * - Memory-efficient: enums are singletons, not new String objects
 *
 * APTROID CONNECTION:
 * These map directly to real marketing events —
 * the same events Aptroid tracks to trigger campaigns.
 */
public enum EventType {

    PAGE_VIEW,       // User visited a page
    PRICING_VIEW,    // User viewed the pricing page (high intent!)
    CLICK,           // User clicked a button or link
    CART_ADD,        // User added item to cart
    CART_ABANDON,    // User left with items in cart (trigger email!)
    PURCHASE,        // User completed a purchase
    EMAIL_OPEN,      // User opened a marketing email
    EMAIL_CLICK,     // User clicked link inside email
    LOGIN,           // User logged in
    LOGOUT           // User logged out
}

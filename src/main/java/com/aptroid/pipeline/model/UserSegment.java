package com.aptroid.pipeline.model;

/**
 * Segment a user belongs to, based on their behavior profile.
 *
 * APTROID CONNECTION:
 * This is exactly what marketing automation does — segments users
 * into buckets so different campaigns target different groups.
 *
 * HIGH_VALUE   → bought before, engaged often → upsell campaign
 * AT_RISK      → hasn't been active → re-engagement email
 * HOT_LEAD     → viewed pricing multiple times → demo offer email
 * CART_ABANDON → left items in cart → reminder email with discount
 * NEW          → just arrived → welcome series
 * UNKNOWN      → not enough data yet
 */
public enum UserSegment {
    HIGH_VALUE,
    AT_RISK,
    HOT_LEAD,
    CART_ABANDONER,
    NEW_USER,
    UNKNOWN
}

package com.company.bsmsvc.application.util;

/**
 * Utilities for safe pagination parameter handling.
 * Validates and clamps incoming page/size values so no controller or service
 * can accidentally trigger an unbounded database scan.
 */
public final class PaginationUtils {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private PaginationUtils() {
    }

    /** Returns page clamped to [0, Integer.MAX_VALUE]. */
    public static int clampPage(int page) {
        return Math.max(0, page);
    }

    /**
     * Returns size in [1, MAX_PAGE_SIZE].
     * Values below 1 fall back to DEFAULT_PAGE_SIZE; values above MAX are capped.
     */
    public static int clampSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}

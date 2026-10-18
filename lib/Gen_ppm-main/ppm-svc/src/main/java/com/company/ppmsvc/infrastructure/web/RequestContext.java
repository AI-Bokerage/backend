package com.company.ppmsvc.infrastructure.web;

/**
 * Thread-local holder for per-request metadata (request ID, client IP).
 * Populated by {@link PpmRequestContextFilter} and cleared in its
 * {@code finally} block.
 */
public final class RequestContext {

    public record Info(String requestId, String clientIp) {}

    private static final ThreadLocal<Info> HOLDER = new ThreadLocal<>();

    private RequestContext() {}

    public static void set(Info info) { HOLDER.set(info); }
    public static Info  get()         { return HOLDER.get(); }
    public static void  clear()       { HOLDER.remove(); }
}

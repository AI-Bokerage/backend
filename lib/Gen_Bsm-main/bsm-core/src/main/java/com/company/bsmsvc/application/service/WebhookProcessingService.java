package com.company.bsmsvc.application.service;

/**
 * Processes inbound Stripe/Razorpay webhook payloads, verifying signatures and applying the
 * resulting payment/subscription state changes. Not auto-configured by the starter — wire
 * manually behind your own webhook HTTP endpoint.
 */
public interface WebhookProcessingService {
    void processStripeWebhook(String payload, String signature);
    void processRazorpayWebhook(String payload, String signature);
}

package com.company.bsmsvc.api.controller;

import com.company.bsmsvc.application.service.WebhookProcessingService;
import com.company.bsmsvc.domain.exception.WebhookVerificationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/bsm/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Provider webhook receivers")
public class StripeWebhookController {

    private final WebhookProcessingService webhookProcessingService;

    @PostMapping("/stripe")
    @Operation(summary = "Receive and process Stripe webhook events")
    public ResponseEntity<Void> stripeWebhook(
        @RequestBody String payload,
        @RequestHeader(value = "Stripe-Signature", required = false) String signature
    ) {
        if (signature == null || signature.isBlank()) {
            log.warn("Stripe webhook received without Stripe-Signature header");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        try {
            webhookProcessingService.processStripeWebhook(payload, signature);
            return ResponseEntity.ok().build();
        } catch (WebhookVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Concurrent duplicate webhook insertion — idempotent, return 200
            log.info("Stripe webhook duplicate concurrent insert ignored: {}", e.getMessage());
            return ResponseEntity.ok().build();
        }
    }

    @PostMapping("/razorpay")
    @Operation(summary = "Receive and process Razorpay webhook events")
    public ResponseEntity<Void> razorpayWebhook(
        @RequestBody String payload,
        @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature
    ) {
        try {
            webhookProcessingService.processRazorpayWebhook(payload, signature != null ? signature : "");
            return ResponseEntity.ok().build();
        } catch (WebhookVerificationException e) {
            log.warn("Razorpay webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.info("Razorpay webhook duplicate concurrent insert ignored: {}", e.getMessage());
            return ResponseEntity.ok().build();
        }
    }
}

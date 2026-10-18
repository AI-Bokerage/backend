package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.enums.PaymentStatus;
import com.company.bsmsvc.domain.model.Payment;
import com.company.bsmsvc.domain.port.PaymentRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryPaymentRepository implements PaymentRepositoryPort {

    private final ConcurrentHashMap<UUID, Payment> store = new ConcurrentHashMap<>();

    @Override
    public Payment save(Payment payment) {
        if (payment.getId() == null) {
            payment = payment.toBuilder().id(UUID.randomUUID()).build();
        }
        store.put(payment.getId(), payment);
        return payment;
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Payment> findByExternalPaymentId(String externalPaymentId) {
        return store.values().stream()
            .filter(p -> externalPaymentId.equals(p.getExternalPaymentId()))
            .findFirst();
    }

    @Override
    public Optional<Payment> findByExternalChargeId(String externalChargeId) {
        return store.values().stream()
            .filter(p -> externalChargeId.equals(p.getExternalChargeId()))
            .findFirst();
    }

    @Override
    public List<Payment> findByInvoiceId(UUID invoiceId) {
        return store.values().stream()
            .filter(p -> invoiceId.equals(p.getInvoiceId()))
            .toList();
    }

    @Override
    public List<Payment> findPendingOlderThan(Instant threshold) {
        return store.values().stream()
            .filter(p -> p.getStatus() == PaymentStatus.PENDING)
            .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().isBefore(threshold))
            .toList();
    }
}

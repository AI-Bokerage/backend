package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.model.BillingLedgerEntry;
import com.company.bsmsvc.domain.port.BillingLedgerRepositoryPort;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryBillingLedgerRepository implements BillingLedgerRepositoryPort {

    private final ConcurrentHashMap<UUID, BillingLedgerEntry> store = new ConcurrentHashMap<>();

    @Override
    public BillingLedgerEntry save(BillingLedgerEntry entry) {
        if (entry.getId() == null) {
            entry = entry.toBuilder().id(UUID.randomUUID()).build();
        }
        store.put(entry.getId(), entry);
        return entry;
    }
}

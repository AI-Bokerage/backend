package com.company.ppmsvc.common;

import com.company.ppmsvc.common.DomainEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

/**
 * Root base for every PPM domain model.
 *
 * <p>Carries the entity identity ({@code id}), an optimistic-locking
 * {@code version} counter that mirrors the persistence layer, and a transient
 * list of {@link DomainEvent}s collected during a business operation and
 * published after the unit-of-work commits.
 *
 * <p>No JPA or Spring annotations — framework-independent.
 */
@Getter
public abstract class BaseEntity {

    protected final UUID id;
    protected Long version;

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected BaseEntity(UUID id) {
        this.id = Objects.requireNonNull(id, "id must not be null");
    }

    protected void registerEvent(DomainEvent event) {
        domainEvents.add(Objects.requireNonNull(event, "event must not be null"));
    }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> snapshot = Collections.unmodifiableList(new ArrayList<>(domainEvents));
        domainEvents.clear();
        return snapshot;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseEntity other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[id=" + id + ", version=" + version + "]";
    }
}

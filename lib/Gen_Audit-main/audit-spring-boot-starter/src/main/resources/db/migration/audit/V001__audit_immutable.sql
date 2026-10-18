CREATE TABLE audit_immutable (
    audit_id        UUID PRIMARY KEY,
    partition_key   VARCHAR(255) NOT NULL,
    seq             BIGINT NOT NULL CHECK (seq > 0),
    event_type      VARCHAR(255) NOT NULL,
    actor_type      VARCHAR(50) NOT NULL,
    actor_id        VARCHAR(255) NOT NULL,
    category        VARCHAR(50) NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL,
    resource_type   VARCHAR(255),
    resource_id     VARCHAR(255),
    reason          TEXT,
    payload_hash    BYTEA NOT NULL,
    prev_event_hash BYTEA NOT NULL,
    event_hash      BYTEA NOT NULL,
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (partition_key, seq)
);

CREATE INDEX idx_audit_immutable_partition_seq ON audit_immutable (partition_key, seq);

REVOKE UPDATE, DELETE ON audit_immutable FROM PUBLIC;

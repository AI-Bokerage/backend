CREATE TABLE auth_outbox_events (
    id              UUID            NOT NULL,
    event_id        UUID            NOT NULL,
    event_type      VARCHAR(255)    NOT NULL,
    payload         JSONB           NOT NULL,
    user_id         UUID,
    target_exchange VARCHAR(255),
    status          VARCHAR(50)     NOT NULL DEFAULT 'PENDING',
    retry_count     INTEGER         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL,
    published_at    TIMESTAMPTZ,
    last_error      TEXT,
    version         BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_auth_outbox_events   PRIMARY KEY (id),
    CONSTRAINT uq_auth_outbox_event_id UNIQUE      (event_id)
);

CREATE INDEX idx_auth_outbox_status_created_at ON auth_outbox_events (status, created_at);

CREATE TABLE chain_partition (
    partition_key TEXT PRIMARY KEY,
    created_at    TIMESTAMPTZ NOT NULL
);

REVOKE UPDATE, DELETE ON chain_partition FROM PUBLIC;

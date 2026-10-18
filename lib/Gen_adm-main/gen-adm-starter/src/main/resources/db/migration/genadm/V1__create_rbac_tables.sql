CREATE TABLE roles (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    name           VARCHAR(100) NOT NULL,
    description    VARCHAR(500),
    is_system_role BOOLEAN NOT NULL DEFAULT FALSE,
    version        BIGINT,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_roles_tenant_name UNIQUE (tenant_id, name)
);

CREATE TABLE permissions (
    id          UUID PRIMARY KEY,
    code        VARCHAR(150) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_role_assignments (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    user_id     UUID NOT NULL,
    role_id     UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_assignments_tenant_user_role UNIQUE (tenant_id, user_id, role_id)
);

CREATE INDEX idx_user_role_assignments_tenant_user ON user_role_assignments(tenant_id, user_id);

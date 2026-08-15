CREATE TABLE subscriptions (
    subscription_id VARCHAR(64) PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL,
    endpoint_url TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    signing_secret_reference VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE subscription_event_types (
    subscription_id VARCHAR(64) NOT NULL REFERENCES subscriptions(subscription_id) ON DELETE CASCADE,
    event_type VARCHAR(128) NOT NULL,
    PRIMARY KEY (subscription_id, event_type)
);

CREATE TABLE notification_events (
    event_id VARCHAR(64) PRIMARY KEY,
    client_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    delivery_status VARCHAR(32) NOT NULL CHECK (
        delivery_status IN ('PENDING', 'PROCESSING', 'RETRY_SCHEDULED', 'COMPLETED', 'FAILED')
    ),
    subscription_id VARCHAR(64) REFERENCES subscriptions(subscription_id),
    destination_url_snapshot TEXT,
    signing_key_version VARCHAR(64),
    delivery_cycle INTEGER NOT NULL DEFAULT 1 CHECK (delivery_cycle >= 1),
    next_attempt_at TIMESTAMPTZ,
    lease_owner VARCHAR(128),
    lease_until TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    attempt_history_complete BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE delivery_attempts (
    attempt_id UUID PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL REFERENCES notification_events(event_id) ON DELETE CASCADE,
    delivery_cycle INTEGER NOT NULL CHECK (delivery_cycle >= 1),
    attempt_number INTEGER NOT NULL CHECK (attempt_number >= 1),
    origin VARCHAR(32) NOT NULL CHECK (
        origin IN ('INITIAL', 'AUTOMATIC_RETRY', 'MANUAL_REPLAY', 'LEASE_RECOVERY')
    ),
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    result VARCHAR(32) CHECK (
        result IS NULL OR result IN ('SUCCESS', 'RETRYABLE_FAILURE', 'PERMANENT_FAILURE')
    ),
    http_status INTEGER CHECK (http_status IS NULL OR http_status BETWEEN 100 AND 599),
    failure_category VARCHAR(64),
    failure_description VARCHAR(500),
    latency_ms BIGINT CHECK (latency_ms IS NULL OR latency_ms >= 0),
    correlation_id VARCHAR(128) NOT NULL,
    UNIQUE (event_id, delivery_cycle, attempt_number)
);

CREATE INDEX idx_notification_events_client_created
    ON notification_events (client_id, created_at DESC, event_id);

CREATE INDEX idx_notification_events_due
    ON notification_events (delivery_status, next_attempt_at);

CREATE INDEX idx_subscriptions_client_active
    ON subscriptions (client_id, active);

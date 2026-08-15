ALTER TABLE notification_events
    ADD COLUMN delivery_date TIMESTAMPTZ;

CREATE INDEX idx_notification_events_client_status_created
    ON notification_events (client_id, delivery_status, created_at DESC, event_id);

CREATE INDEX idx_notification_events_subscription
    ON notification_events (subscription_id)
    WHERE subscription_id IS NOT NULL;

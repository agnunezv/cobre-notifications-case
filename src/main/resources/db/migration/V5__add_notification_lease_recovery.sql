ALTER TABLE notification_events
    ADD COLUMN lease_recovery_pending BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_notification_events_expired_leases
    ON notification_events (lease_until, event_id)
    WHERE delivery_status = 'PROCESSING'
      AND lease_until IS NOT NULL;

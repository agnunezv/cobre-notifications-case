UPDATE notification_events
SET next_attempt_at = created_at
WHERE delivery_status = 'PENDING'
  AND next_attempt_at IS NULL;

DROP INDEX idx_notification_events_due;

CREATE INDEX idx_notification_events_claimable
    ON notification_events (next_attempt_at, event_id)
    WHERE delivery_status IN ('PENDING', 'RETRY_SCHEDULED')
      AND next_attempt_at IS NOT NULL;

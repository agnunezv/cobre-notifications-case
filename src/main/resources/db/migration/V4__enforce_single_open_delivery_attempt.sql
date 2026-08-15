CREATE UNIQUE INDEX uq_delivery_attempts_open_per_cycle
    ON delivery_attempts (event_id, delivery_cycle)
    WHERE finished_at IS NULL;

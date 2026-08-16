package com.cobre.notifications.application.port.outbound;

import com.cobre.notifications.application.model.NotificationDeliveryBatchResult;
import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import com.cobre.notifications.application.model.WebhookDeliveryOutcome;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

public interface NotificationDeliveryMetrics {

    void recordAttempt(
            @NotNull @Valid PreparedNotificationDelivery delivery, @NotNull @Valid WebhookDeliveryOutcome outcome);

    void recordBatch(@NotNull @Valid NotificationDeliveryBatchResult result, @NotNull Duration duration);

    void recordBatchFailure(@NotNull Duration duration);
}

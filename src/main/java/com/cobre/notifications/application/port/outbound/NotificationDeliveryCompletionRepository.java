package com.cobre.notifications.application.port.outbound;

import com.cobre.notifications.application.model.NotificationDeliveryAttemptCompletion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface NotificationDeliveryCompletionRepository {

    boolean completeIfCurrent(@NotNull @Valid NotificationDeliveryAttemptCompletion completion);
}

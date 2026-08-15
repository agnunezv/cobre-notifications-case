package com.cobre.notifications.application.port.inbound;

import com.cobre.notifications.application.model.ClaimNotificationDeliveriesCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public interface RecoverExpiredNotificationLeasesUseCase {

    int recoverExpired(
            @Min(value = 1, message = "batchSize must be between 1 and 100")
            @Max(
                    value = ClaimNotificationDeliveriesCommand.MAX_BATCH_SIZE,
                    message = "batchSize must be between 1 and 100")
            int batchSize);
}

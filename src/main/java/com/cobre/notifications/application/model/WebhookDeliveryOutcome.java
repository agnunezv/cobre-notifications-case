package com.cobre.notifications.application.model;

import com.cobre.notifications.domain.model.DeliveryAttemptResult;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record WebhookDeliveryOutcome(
        @NotNull DeliveryAttemptResult result,
        @Min(100) @Max(599) Integer httpStatus,
        NotificationDeliveryFailureCategory failureCategory,
        @Size(max = 500) String failureDescription,
        @PositiveOrZero long latencyMs) {

    @AssertTrue(message = "a successful delivery requires a 2xx status and no failure information") public boolean isSuccessConsistent() {
        return result == null
                || result != DeliveryAttemptResult.SUCCESS
                || httpStatus != null
                        && httpStatus >= 200
                        && httpStatus < 300
                        && failureCategory == null
                        && failureDescription == null;
    }

    @AssertTrue(message = "a failed delivery requires consistent failure information") public boolean isFailureConsistent() {
        if (result == null || result == DeliveryAttemptResult.SUCCESS) {
            return true;
        }

        boolean hasDescription = failureDescription != null && !failureDescription.isBlank();
        boolean httpFailureIsConsistent = failureCategory != NotificationDeliveryFailureCategory.HTTP_RESPONSE
                || httpStatus != null && (httpStatus < 200 || httpStatus >= 300);
        boolean transportFailureIsConsistent =
                failureCategory == NotificationDeliveryFailureCategory.HTTP_RESPONSE || httpStatus == null;

        return failureCategory != null && hasDescription && httpFailureIsConsistent && transportFailureIsConsistent;
    }
}

package com.cobre.notifications.application.model;

import com.cobre.notifications.domain.model.DeliveryStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record NotificationEventQuery(
        @NotBlank(message = "An authenticated client is required") @Size(max = 64, message = "clientId must not exceed 64 characters") String clientId,

        Instant createdFrom,
        Instant createdTo,
        DeliveryStatus deliveryStatus,

        @PositiveOrZero(message = "page must be greater than or equal to zero") int page,

        @Min(value = 1, message = "size must be between 1 and 100") @Max(value = MAX_PAGE_SIZE, message = "size must be between 1 and 100") int size) {

    public static final int MAX_PAGE_SIZE = 100;

    @AssertTrue(message = "created_from must be earlier than created_to") public boolean isCreationDateRangeValid() {
        return createdFrom == null || createdTo == null || createdFrom.isBefore(createdTo);
    }
}

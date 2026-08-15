package com.cobre.notifications.application.model;

public enum DeliveryPreparationFailureCategory {
    SUBSCRIPTION_NOT_FOUND("No active subscription matches the notification client and event type"),
    AMBIGUOUS_SUBSCRIPTION("Multiple active subscriptions match the notification client and event type"),
    INVALID_DESTINATION("The resolved notification destination is invalid");

    private final String description;

    DeliveryPreparationFailureCategory(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}

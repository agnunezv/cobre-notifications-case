package com.cobre.notifications.adapter.out.webhook;

import com.cobre.notifications.application.model.NotificationDeliveryFailureCategory;
import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import com.cobre.notifications.application.model.WebhookDeliveryOutcome;
import com.cobre.notifications.application.port.outbound.NotificationDeliveryGateway;
import com.cobre.notifications.domain.model.DeliveryAttemptResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import javax.net.ssl.SSLException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Validated
public class HttpsNotificationDeliveryAdapter implements NotificationDeliveryGateway {

    static final String EVENT_ID_HEADER = "X-Notification-Event-Id";
    static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private final RestClient restClient;

    public HttpsNotificationDeliveryAdapter(@Qualifier("notificationDeliveryRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public WebhookDeliveryOutcome deliver(PreparedNotificationDelivery delivery) {
        long startedAt = System.nanoTime();

        try {
            int httpStatus = restClient
                    .post()
                    .uri(delivery.destination().endpointUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(EVENT_ID_HEADER, delivery.eventId())
                    .header(CORRELATION_ID_HEADER, delivery.correlationId())
                    .body(WebhookNotificationRequest.from(delivery))
                    .exchange((request, response) -> response.getStatusCode().value());

            return outcomeFrom(httpStatus, elapsedMillisecondsSince(startedAt));
        } catch (ResourceAccessException exception) {
            return outcomeFrom(exception, elapsedMillisecondsSince(startedAt));
        } catch (RestClientException exception) {
            return failureOutcome(
                    DeliveryAttemptResult.PERMANENT_FAILURE,
                    NotificationDeliveryFailureCategory.HTTP_CLIENT_ERROR,
                    "The HTTP client could not create or process the webhook request",
                    elapsedMillisecondsSince(startedAt));
        }
    }

    private WebhookDeliveryOutcome outcomeFrom(int httpStatus, long latencyMs) {
        if (httpStatus < 100 || httpStatus > 599) {
            return failureOutcome(
                    DeliveryAttemptResult.PERMANENT_FAILURE,
                    NotificationDeliveryFailureCategory.HTTP_CLIENT_ERROR,
                    "The webhook endpoint returned an unsupported HTTP status",
                    latencyMs);
        }
        if (httpStatus >= 200 && httpStatus < 300) {
            return new WebhookDeliveryOutcome(DeliveryAttemptResult.SUCCESS, httpStatus, null, null, latencyMs);
        }

        DeliveryAttemptResult result = isRetryable(httpStatus)
                ? DeliveryAttemptResult.RETRYABLE_FAILURE
                : DeliveryAttemptResult.PERMANENT_FAILURE;
        return new WebhookDeliveryOutcome(
                result,
                httpStatus,
                NotificationDeliveryFailureCategory.HTTP_RESPONSE,
                "The webhook endpoint returned HTTP " + httpStatus,
                latencyMs);
    }

    private WebhookDeliveryOutcome outcomeFrom(ResourceAccessException exception, long latencyMs) {
        if (hasCause(exception, HttpTimeoutException.class) || hasCause(exception, SocketTimeoutException.class)) {
            return failureOutcome(
                    DeliveryAttemptResult.RETRYABLE_FAILURE,
                    NotificationDeliveryFailureCategory.TIMEOUT,
                    "The webhook request timed out",
                    latencyMs);
        }
        if (hasCause(exception, SSLException.class)) {
            return failureOutcome(
                    DeliveryAttemptResult.PERMANENT_FAILURE,
                    NotificationDeliveryFailureCategory.TLS_ERROR,
                    "The webhook endpoint failed TLS validation",
                    latencyMs);
        }
        if (hasCause(exception, ConnectException.class)
                || hasCause(exception, NoRouteToHostException.class)
                || hasCause(exception, UnknownHostException.class)) {
            return failureOutcome(
                    DeliveryAttemptResult.RETRYABLE_FAILURE,
                    NotificationDeliveryFailureCategory.CONNECTION_ERROR,
                    "The webhook endpoint could not be reached",
                    latencyMs);
        }

        return failureOutcome(
                DeliveryAttemptResult.RETRYABLE_FAILURE,
                NotificationDeliveryFailureCategory.CONNECTION_ERROR,
                "The webhook request failed before receiving a response",
                latencyMs);
    }

    private WebhookDeliveryOutcome failureOutcome(
            DeliveryAttemptResult result,
            NotificationDeliveryFailureCategory failureCategory,
            String failureDescription,
            long latencyMs) {
        return new WebhookDeliveryOutcome(result, null, failureCategory, failureDescription, latencyMs);
    }

    private boolean isRetryable(int httpStatus) {
        return httpStatus == 408 || httpStatus == 429 || httpStatus >= 500;
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> causeType) {
        Throwable current = throwable;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private long elapsedMillisecondsSince(long startedAt) {
        long elapsedNanos = Math.max(0, System.nanoTime() - startedAt);
        return Duration.ofNanos(elapsedNanos).toMillis();
    }

    private record WebhookNotificationRequest(
            @JsonProperty("event_id") String eventId,
            @JsonProperty("event_type") String eventType,
            String content) {

        private static WebhookNotificationRequest from(PreparedNotificationDelivery delivery) {
            return new WebhookNotificationRequest(delivery.eventId(), delivery.eventType(), delivery.content());
        }
    }
}

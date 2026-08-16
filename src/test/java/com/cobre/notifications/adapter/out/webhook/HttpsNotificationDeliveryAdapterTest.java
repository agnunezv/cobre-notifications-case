package com.cobre.notifications.adapter.out.webhook;

import static com.cobre.notifications.adapter.out.webhook.HttpsNotificationDeliveryAdapter.CORRELATION_ID_HEADER;
import static com.cobre.notifications.adapter.out.webhook.HttpsNotificationDeliveryAdapter.IDEMPOTENCY_KEY_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.cobre.notifications.application.model.NotificationDeliveryFailureCategory;
import com.cobre.notifications.application.model.PreparedNotificationDelivery;
import com.cobre.notifications.application.model.WebhookDeliveryOutcome;
import com.cobre.notifications.domain.model.DeliveryAttemptResult;
import com.cobre.notifications.domain.model.NotificationDestination;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpsNotificationDeliveryAdapterTest {

    private static final URI ENDPOINT = URI.create("https://hooks.example.com/notifications");
    private static final UUID ATTEMPT_ID = UUID.fromString("2dcf5bb1-9c8e-4a34-9401-89f7398c44ad");
    private static final String CORRELATION_ID = ATTEMPT_ID.toString();

    private MockRestServiceServer server;
    private RestClient restClient;

    @BeforeEach
    void configureHttpClient() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    @AfterEach
    void verifyHttpExchange() {
        server.verify();
    }

    @Test
    void sendsTheTenantAwareNotificationEnvelopeAndDeliveryHeaders() {
        server.expect(once(), requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(IDEMPOTENCY_KEY_HEADER, "EVT001"))
                .andExpect(header(CORRELATION_ID_HEADER, CORRELATION_ID))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "event_id": "EVT001",
                          "client_id": "CLIENT001",
                          "event_type": "credit_payment",
                          "content": "Payment confirmed"
                        }
                        """))
                .andRespond(withStatus(HttpStatusCode.valueOf(202)));

        WebhookDeliveryOutcome result = adapter().deliver(delivery());

        assertThat(result.result()).isEqualTo(DeliveryAttemptResult.SUCCESS);
        assertThat(result.httpStatus()).isEqualTo(202);
        assertThat(result.failureCategory()).isNull();
        assertThat(result.failureDescription()).isNull();
        assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void keepsTheIdempotencyKeyStableWhileCorrelationChangesBetweenAttempts() {
        UUID retryAttemptId = UUID.fromString("851f5a3a-b06f-41f6-a84a-d73581456cb5");
        server.expect(once(), requestTo(ENDPOINT))
                .andExpect(header(IDEMPOTENCY_KEY_HEADER, "EVT001"))
                .andExpect(header(CORRELATION_ID_HEADER, CORRELATION_ID))
                .andRespond(withStatus(HttpStatusCode.valueOf(500)));
        server.expect(once(), requestTo(ENDPOINT))
                .andExpect(header(IDEMPOTENCY_KEY_HEADER, "EVT001"))
                .andExpect(header(CORRELATION_ID_HEADER, retryAttemptId.toString()))
                .andRespond(withStatus(HttpStatusCode.valueOf(200)));

        assertThat(adapter().deliver(delivery()).result()).isEqualTo(DeliveryAttemptResult.RETRYABLE_FAILURE);
        assertThat(adapter().deliver(delivery(retryAttemptId, 2)).result()).isEqualTo(DeliveryAttemptResult.SUCCESS);
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 429, 500, 503})
    void classifiesTransientHttpResponsesAsRetryable(int httpStatus) {
        expectStatus(httpStatus);

        WebhookDeliveryOutcome result = adapter().deliver(delivery());

        assertThat(result.result()).isEqualTo(DeliveryAttemptResult.RETRYABLE_FAILURE);
        assertThat(result.httpStatus()).isEqualTo(httpStatus);
        assertThat(result.failureCategory()).isEqualTo(NotificationDeliveryFailureCategory.HTTP_RESPONSE);
        assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 400, 404})
    void classifiesOtherNonSuccessfulHttpResponsesAsPermanent(int httpStatus) {
        expectStatus(httpStatus);

        WebhookDeliveryOutcome result = adapter().deliver(delivery());

        assertThat(result.result()).isEqualTo(DeliveryAttemptResult.PERMANENT_FAILURE);
        assertThat(result.httpStatus()).isEqualTo(httpStatus);
        assertThat(result.failureCategory()).isEqualTo(NotificationDeliveryFailureCategory.HTTP_RESPONSE);
    }

    @Test
    void rejectsUnsupportedHttpStatusesWithoutProducingAnUnpersistableResult() {
        expectStatus(700);

        WebhookDeliveryOutcome result = adapter().deliver(delivery());

        assertThat(result.result()).isEqualTo(DeliveryAttemptResult.PERMANENT_FAILURE);
        assertThat(result.httpStatus()).isNull();
        assertThat(result.failureCategory()).isEqualTo(NotificationDeliveryFailureCategory.HTTP_CLIENT_ERROR);
    }

    @Test
    void classifiesTimeoutsAsRetryableWithoutExposingExceptionDetails() {
        server.expect(once(), requestTo(ENDPOINT))
                .andRespond(withException(new SocketTimeoutException("sensitive timeout detail")));

        WebhookDeliveryOutcome result = adapter().deliver(delivery());

        assertThat(result.result()).isEqualTo(DeliveryAttemptResult.RETRYABLE_FAILURE);
        assertThat(result.httpStatus()).isNull();
        assertThat(result.failureCategory()).isEqualTo(NotificationDeliveryFailureCategory.TIMEOUT);
        assertThat(result.failureDescription())
                .isEqualTo("The webhook request timed out")
                .doesNotContain("sensitive");
        assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void classifiesConnectionFailuresAsRetryable() {
        server.expect(once(), requestTo(ENDPOINT))
                .andRespond(withException(new ConnectException("connection refused")));

        WebhookDeliveryOutcome result = adapter().deliver(delivery());

        assertThat(result.result()).isEqualTo(DeliveryAttemptResult.RETRYABLE_FAILURE);
        assertThat(result.httpStatus()).isNull();
        assertThat(result.failureCategory()).isEqualTo(NotificationDeliveryFailureCategory.CONNECTION_ERROR);
    }

    @Test
    void classifiesTlsFailuresAsPermanent() {
        server.expect(once(), requestTo(ENDPOINT))
                .andRespond(withException(new SSLHandshakeException("certificate rejected")));

        WebhookDeliveryOutcome result = adapter().deliver(delivery());

        assertThat(result.result()).isEqualTo(DeliveryAttemptResult.PERMANENT_FAILURE);
        assertThat(result.httpStatus()).isNull();
        assertThat(result.failureCategory()).isEqualTo(NotificationDeliveryFailureCategory.TLS_ERROR);
    }

    private void expectStatus(int httpStatus) {
        server.expect(once(), requestTo(ENDPOINT)).andRespond(withStatus(HttpStatusCode.valueOf(httpStatus)));
    }

    private HttpsNotificationDeliveryAdapter adapter() {
        return new HttpsNotificationDeliveryAdapter(restClient);
    }

    private PreparedNotificationDelivery delivery() {
        return delivery(ATTEMPT_ID, 1);
    }

    private PreparedNotificationDelivery delivery(UUID attemptId, int attemptNumber) {
        return new PreparedNotificationDelivery(
                attemptId,
                "EVT001",
                "CLIENT001",
                "credit_payment",
                "Payment confirmed",
                new NotificationDestination("SUB001", ENDPOINT),
                1,
                attemptNumber,
                attemptId.toString(),
                Instant.parse("2026-08-15T12:00:00Z"));
    }
}

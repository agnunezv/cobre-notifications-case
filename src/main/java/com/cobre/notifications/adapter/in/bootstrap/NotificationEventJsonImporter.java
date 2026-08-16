package com.cobre.notifications.adapter.in.bootstrap;

import com.cobre.notifications.application.port.inbound.ImportNotificationEventsUseCase;
import com.cobre.notifications.domain.model.DeliveryStatus;
import com.cobre.notifications.domain.model.NotificationEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "notifications.json-import", name = "enabled", havingValue = "true")
public class NotificationEventJsonImporter implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationEventJsonImporter.class);

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final ImportNotificationEventsUseCase importUseCase;
    private final Clock clock;
    private final Resource jsonResource;

    public NotificationEventJsonImporter(
            ObjectMapper objectMapper,
            Validator validator,
            ImportNotificationEventsUseCase importUseCase,
            Clock clock,
            @Value("${notifications.json-import.location}") Resource jsonResource) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.importUseCase = importUseCase;
        this.clock = clock;
        this.jsonResource = jsonResource;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        NotificationEventsJsonFile jsonFile;
        try (InputStream input = jsonResource.getInputStream()) {
            jsonFile = objectMapper.readValue(input, NotificationEventsJsonFile.class);
        }
        validate(jsonFile);

        Instant acceptedAt = clock.instant();
        List<NotificationEvent> events = jsonFile.events().stream()
                .map(event -> toDomain(event, acceptedAt))
                .toList();

        int inserted = importUseCase.importIfAbsent(events);
        LOGGER.info(
                "Notification JSON import completed: {} inserted, {} already present",
                inserted,
                events.size() - inserted);
    }

    private void validate(NotificationEventsJsonFile jsonFile) {
        Set<ConstraintViolation<NotificationEventsJsonFile>> violations = validator.validate(jsonFile);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException("Notification JSON validation failed", violations);
        }
    }

    private NotificationEvent toDomain(NotificationEventJsonRecord item, Instant acceptedAt) {
        return new NotificationEvent(
                item.eventId(),
                item.clientId(),
                item.eventType(),
                item.content(),
                acceptedAt,
                item.deliveryDate(),
                DeliveryStatus.valueOf(item.deliveryStatus().toUpperCase(Locale.ROOT)));
    }
}

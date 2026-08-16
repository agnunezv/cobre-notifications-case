package com.cobre.notifications.adapter.in.web.security;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoringPrincipalTest {

    @Test
    void requiresANonBlankBoundedSubject() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();

            assertThat(validator.validate(new MonitoringPrincipal("INTERNAL_MONITORING")))
                    .isEmpty();
            assertThat(validator.validate(new MonitoringPrincipal(" ")))
                    .hasSize(1);
            assertThat(validator.validate(new MonitoringPrincipal("m".repeat(65))))
                    .hasSize(1);
        }
    }
}

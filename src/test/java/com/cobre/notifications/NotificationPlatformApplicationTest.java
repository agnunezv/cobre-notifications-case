package com.cobre.notifications;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationPlatformApplicationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationPlatformApplication.class)
            .withPropertyValues(
                    "spring.autoconfigure.exclude=" + String.join(",",
                            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
                            "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"));

    @Test
    void startsTheApplicationLayerWithoutExternalInfrastructure() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(NotificationPlatformApplication.class);
            assertThat(NotificationPlatformApplication.class)
                    .hasAnnotation(SpringBootApplication.class);
        });
    }
}

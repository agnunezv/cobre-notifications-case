package com.cobre.notifications.config;

import com.cobre.notifications.adapter.in.web.security.BearerTokenAuthenticationEntryPoint;
import com.cobre.notifications.adapter.in.web.security.BearerTokenAuthenticationFilter;
import com.cobre.notifications.adapter.in.web.security.BearerTokenAuthenticationProvider;
import com.cobre.notifications.adapter.in.web.security.BearerTokenRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(NotificationSecurityProperties.class)
public class SecurityConfiguration {

    @Bean
    BearerTokenRegistry bearerTokenRegistry(NotificationSecurityProperties properties) {
        return new BearerTokenRegistry(properties);
    }

    @Bean
    BearerTokenAuthenticationProvider bearerTokenAuthenticationProvider(BearerTokenRegistry tokenRegistry) {
        return new BearerTokenAuthenticationProvider(tokenRegistry);
    }

    @Bean
    BearerTokenAuthenticationEntryPoint bearerTokenAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new BearerTokenAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            BearerTokenAuthenticationProvider authenticationProvider,
            BearerTokenAuthenticationEntryPoint authenticationEntryPoint) throws Exception {
        var authenticationManager = new ProviderManager(authenticationProvider);
        BearerTokenAuthenticationFilter bearerTokenFilter =
                new BearerTokenAuthenticationFilter(authenticationManager);

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers(
                                "/actuator/prometheus",
                                "/actuator/metrics",
                                "/actuator/metrics/**",
                                "/actuator/info")
                        .hasRole("MONITORING")
                        .requestMatchers("/internal/monitoring/**").hasRole("MONITORING")
                        .requestMatchers("/notification_events/**").hasRole("CLIENT")
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(bearerTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}

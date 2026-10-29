package com.shreeya.vehicletitle.infrastructure.integration;

import com.shreeya.vehicletitle.application.OutboxPublisher;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Chooses how outbox events leave the service, based on {@code app.outbox.publisher}.
 */
@Configuration
public class OutboxPublisherConfig {

    @Bean
    @ConditionalOnProperty(name = "app.outbox.publisher", havingValue = "logging", matchIfMissing = true)
    OutboxPublisher loggingOutboxPublisher() {
        return new LoggingOutboxPublisher();
    }

    @Bean
    @ConditionalOnProperty(name = "app.outbox.publisher", havingValue = "http")
    OutboxPublisher httpOutboxPublisher(RestClient.Builder restClientBuilder,
                                        CircuitBreakerRegistry circuitBreakerRegistry,
                                        @Value("${app.outbox.webhook-url}") String webhookUrl,
                                        @Value("${app.outbox.webhook-timeout:2s}") Duration timeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        // The auto-configured builder carries Micrometer observation, so every webhook call becomes
        // a child span and sends a W3C traceparent header downstream.
        RestClient restClient = restClientBuilder.requestFactory(requestFactory).build();
        return new HttpOutboxPublisher(restClient, webhookUrl,
                circuitBreakerRegistry.circuitBreaker("titleRegistry"));
    }
}

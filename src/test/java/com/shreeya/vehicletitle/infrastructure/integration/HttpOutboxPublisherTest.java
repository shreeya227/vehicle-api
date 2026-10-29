package com.shreeya.vehicletitle.infrastructure.integration;

import com.shreeya.vehicletitle.application.DownstreamUnavailableException;
import com.shreeya.vehicletitle.domain.OutboxEvent;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServiceUnavailable;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpOutboxPublisherTest {

    private static final String WEBHOOK_URL = "http://registry.test/title-events";

    private MockRestServiceServer registry;
    private CircuitBreaker circuitBreaker;
    private HttpOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        registry = MockRestServiceServer.bindTo(builder).build();
        circuitBreaker = CircuitBreaker.of("titleRegistry", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .ignoreExceptions(HttpClientErrorException.class)
                .build());
        publisher = new HttpOutboxPublisher(builder.build(), WEBHOOK_URL, circuitBreaker);
    }

    @Test
    void postsEventWithIdempotencyKeyAndEventType() {
        OutboxEvent event = event();
        registry.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", event.getId().toString()))
                .andExpect(header("X-Event-Type", "TITLE_TRANSACTION_RECEIVED"))
                .andRespond(withSuccess());

        publisher.publish(event);

        registry.verify();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void opensCircuitAfterRepeatedFailuresAndStopsCallingTheRegistry() {
        registry.expect(ExpectedCount.times(4), requestTo(WEBHOOK_URL))
                .andRespond(withServiceUnavailable());

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> publisher.publish(event()))
                    .isInstanceOf(HttpServerErrorException.class);
        }
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // The fifth event is rejected by the open circuit without any HTTP call.
        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(DownstreamUnavailableException.class);
        registry.verify();
    }

    @Test
    void clientErrorsDoNotOpenTheCircuit() {
        registry.expect(ExpectedCount.times(4), requestTo(WEBHOOK_URL))
                .andRespond(withBadRequest());

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> publisher.publish(event()))
                    .isInstanceOf(HttpClientErrorException.class);
        }

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        registry.verify();
    }

    private static OutboxEvent event() {
        return new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(),
                "TITLE_TRANSACTION_RECEIVED", "{\"vin\":\"1HGCM82633A004352\"}",
                Instant.parse("2026-10-29T12:00:00Z"));
    }
}

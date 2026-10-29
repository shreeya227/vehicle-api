package com.shreeya.vehicletitle.infrastructure.integration;

import com.shreeya.vehicletitle.application.DownstreamUnavailableException;
import com.shreeya.vehicletitle.application.OutboxPublisher;
import com.shreeya.vehicletitle.domain.OutboxEvent;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Delivers outbox events to the title registry webhook over HTTP, guarded by a Resilience4j
 * circuit breaker.
 *
 * The outbox already retries failed events with exponential backoff. The circuit breaker adds the
 * missing piece: when the registry is clearly down, the service stops calling it altogether
 * instead of sending every pending event into a failing dependency.
 */
public class HttpOutboxPublisher implements OutboxPublisher {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    static final String EVENT_TYPE_HEADER = "X-Event-Type";

    private final RestClient restClient;
    private final String webhookUrl;
    private final CircuitBreaker circuitBreaker;

    public HttpOutboxPublisher(RestClient restClient, String webhookUrl, CircuitBreaker circuitBreaker) {
        this.restClient = restClient;
        this.webhookUrl = webhookUrl;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public void publish(OutboxEvent event) {
        try {
            circuitBreaker.executeRunnable(() -> restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    // The registry de-duplicates on this key, which makes at-least-once delivery safe.
                    .header(IDEMPOTENCY_KEY_HEADER, event.getId().toString())
                    .header(EVENT_TYPE_HEADER, event.getEventType())
                    .body(event.getPayload())
                    .retrieve()
                    .toBodilessEntity());
        } catch (CallNotPermittedException exception) {
            throw new DownstreamUnavailableException("Title registry", exception);
        }
    }
}

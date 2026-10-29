package com.shreeya.vehicletitle.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Points the gateway at a port where nothing is listening, as if the API were down.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "gateway.vehicle-api-uri=http://localhost:1")
@AutoConfigureWebTestClient
class GatewayFallbackTest {

    @Autowired
    private WebTestClient client;

    @Test
    void returnsProblemDetailWhenTheApiIsDown() {
        client.post().uri("/api/v1/title-transactions")
                .header("Content-Type", "application/json")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("Retry-After", "30")
                .expectBody()
                .jsonPath("$.title").isEqualTo("Service unavailable")
                .jsonPath("$.status").isEqualTo(503);
    }
}

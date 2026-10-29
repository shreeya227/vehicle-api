package com.shreeya.vehicletitle.gateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayRoutesTest {

    private static final MockWebServer VEHICLE_API = startServer();

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void routeToMockApi(DynamicPropertyRegistry registry) {
        registry.add("gateway.vehicle-api-uri", () -> "http://localhost:" + VEHICLE_API.getPort());
    }

    @AfterAll
    static void stopServer() throws IOException {
        VEHICLE_API.shutdown();
    }

    @Test
    void forwardsRestCallsWithPathAndApiKey() throws InterruptedException {
        VEHICLE_API.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"RECEIVED\"}"));

        client.get().uri("/api/v1/title-transactions/123")
                .header("X-API-Key", "test-key")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("RECEIVED");

        RecordedRequest forwarded = VEHICLE_API.takeRequest(5, TimeUnit.SECONDS);
        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getPath()).isEqualTo("/api/v1/title-transactions/123");
        assertThat(forwarded.getHeader("X-API-Key")).isEqualTo("test-key");
        assertThat(forwarded.getHeader(GatewayRoutes.GATEWAY_HEADER)).isEqualTo("vehicle-title-gateway");
    }

    @Test
    void retriesTransientFailuresOnReads() throws InterruptedException {
        VEHICLE_API.enqueue(new MockResponse().setResponseCode(503));
        VEHICLE_API.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"RECEIVED\"}"));
        int before = VEHICLE_API.getRequestCount();

        client.get().uri("/api/v1/title-transactions/456")
                .exchange()
                .expectStatus().isOk();

        assertThat(VEHICLE_API.getRequestCount() - before).isEqualTo(2);
        // Drain the recorded requests so other tests in this class start from an empty queue.
        VEHICLE_API.takeRequest(5, TimeUnit.SECONDS);
        VEHICLE_API.takeRequest(5, TimeUnit.SECONDS);
    }

    private static MockWebServer startServer() {
        try {
            MockWebServer server = new MockWebServer();
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

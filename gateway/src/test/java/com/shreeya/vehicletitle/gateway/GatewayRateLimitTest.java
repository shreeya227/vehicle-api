package com.shreeya.vehicletitle.gateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the real RedisRateLimiter against a real Redis.
 *
 * The limits are set so a request costs 60 tokens and the bucket refills at 1 token per second
 * with room for 120: each partner gets exactly two requests, and a third would have to wait a
 * minute. That makes the 429 deterministic instead of depending on how fast the test runs.
 *
 * CI starts a Redis service for this. Locally, start one (for example `redis-server`) or the test
 * is skipped.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "gateway.rate-limit.replenish-rate=1",
        "gateway.rate-limit.burst-capacity=120",
        "gateway.rate-limit.requested-tokens=60"
})
@AutoConfigureWebTestClient
class GatewayRateLimitTest {

    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    private static final int REDIS_PORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
    private static final MockWebServer VEHICLE_API = startServer();

    @Autowired
    private WebTestClient client;

    @BeforeAll
    static void requireRedis() {
        assumeTrue(redisIsReachable(), "Redis is not running on " + REDIS_HOST + ":" + REDIS_PORT);
    }

    @AfterAll
    static void stopServer() throws IOException {
        VEHICLE_API.shutdown();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("gateway.vehicle-api-uri", () -> "http://localhost:" + VEHICLE_API.getPort());
        registry.add("spring.data.redis.host", () -> REDIS_HOST);
        registry.add("spring.data.redis.port", () -> REDIS_PORT);
    }

    @Test
    void rejectsAPartnerOverItsLimitWithoutAffectingOtherPartners() {
        String partnerA = "partner-a-" + UUID.randomUUID();
        String partnerB = "partner-b-" + UUID.randomUUID();
        int forwardedBefore = VEHICLE_API.getRequestCount();

        for (int i = 0; i < 3; i++) {
            VEHICLE_API.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"status\":\"RECEIVED\"}"));
        }

        get(partnerA).expectStatus().isOk()
                .expectHeader().exists("X-RateLimit-Remaining");
        get(partnerA).expectStatus().isOk();
        get(partnerA).expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("X-RateLimit-Remaining", "0");

        // A different partner has its own bucket.
        get(partnerB).expectStatus().isOk();

        // The rejected request never reached the API: 2 for partner A, 1 for partner B.
        assertThat(VEHICLE_API.getRequestCount() - forwardedBefore).isEqualTo(3);
    }

    private WebTestClient.ResponseSpec get(String apiKey) {
        return client.get().uri("/api/v1/title-transactions/123")
                .header(RateLimitConfig.API_KEY_HEADER, apiKey)
                .exchange();
    }

    private static MockWebServer startServer() {
        try {
            MockWebServer server = new MockWebServer();
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean redisIsReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(REDIS_HOST, REDIS_PORT), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}

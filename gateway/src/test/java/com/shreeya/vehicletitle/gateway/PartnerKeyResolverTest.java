package com.shreeya.vehicletitle.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/** Decides whose bucket a request counts against. No Redis needed. */
class PartnerKeyResolverTest {

    private final KeyResolver resolver = new RateLimitConfig().partnerKeyResolver();

    @Test
    void bucketsByApiKeyWhenPresent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/title-transactions/1")
                .header(RateLimitConfig.API_KEY_HEADER, "partner-123")
                .remoteAddress(new InetSocketAddress("10.0.0.7", 51000)));

        assertThat(resolver.resolve(exchange).block()).isEqualTo("key:partner-123");
    }

    @Test
    void fallsBackToClientIpWithoutAKey() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/title-transactions/1")
                .remoteAddress(new InetSocketAddress("10.0.0.7", 51000)));

        assertThat(resolver.resolve(exchange).block()).isEqualTo("ip:10.0.0.7");
    }

    @Test
    void treatsABlankKeyAsMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/title-transactions/1")
                .header(RateLimitConfig.API_KEY_HEADER, "  ")
                .remoteAddress(new InetSocketAddress("10.0.0.7", 51000)));

        assertThat(resolver.resolve(exchange).block()).isEqualTo("ip:10.0.0.7");
    }
}

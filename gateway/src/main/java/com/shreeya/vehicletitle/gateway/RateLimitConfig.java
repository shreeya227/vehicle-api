package com.shreeya.vehicletitle.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Per-client rate limiting at the edge.
 *
 * Each partner gets its own token bucket in Redis, keyed by its API key, so one noisy partner
 * cannot use up capacity meant for the others. Callers without a key are bucketed by IP address.
 * Over the limit, the gateway answers 429 before the request reaches the API.
 *
 * If Redis is unreachable, Spring Cloud Gateway's RedisRateLimiter lets requests through rather
 * than rejecting them: a Redis outage should not take down partner traffic.
 */
@Configuration
public class RateLimitConfig {

    static final String API_KEY_HEADER = "X-API-Key";

    @Bean
    @Primary
    KeyResolver partnerKeyResolver() {
        return exchange -> {
            String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
            if (apiKey != null && !apiKey.isBlank()) {
                return Mono.just("key:" + apiKey);
            }
            InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
            String ip = remote != null && remote.getAddress() != null
                    ? remote.getAddress().getHostAddress()
                    : "unknown";
            return Mono.just("ip:" + ip);
        };
    }

    /**
     * replenishRate: tokens added per second. burstCapacity: the most a client can save up.
     * requestedTokens: what one request costs. The defaults allow a steady 10 requests per second
     * per partner with short bursts of up to 20.
     */
    @Bean
    @Primary
    RedisRateLimiter partnerRateLimiter(
            @Value("${gateway.rate-limit.replenish-rate:10}") int replenishRate,
            @Value("${gateway.rate-limit.burst-capacity:20}") int burstCapacity,
            @Value("${gateway.rate-limit.requested-tokens:1}") int requestedTokens) {
        return new RedisRateLimiter(replenishRate, burstCapacity, requestedTokens);
    }
}

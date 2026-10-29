package com.shreeya.vehicletitle.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

/**
 * Routes partner traffic to the Vehicle Title API.
 *
 * Every route sits behind a circuit breaker with a fallback. Reads are retried on transient
 * failures; writes are not, because a submission is only safe to replay with its idempotency key,
 * which the client controls.
 */
@Configuration
public class GatewayRoutes {

    static final String GATEWAY_HEADER = "X-Gateway";

    @Bean
    RouteLocator vehicleTitleRoutes(RouteLocatorBuilder routes,
                                    @Value("${gateway.vehicle-api-uri}") String vehicleApiUri) {
        return routes.routes()
                .route("vehicle-title-rest", route -> route
                        .path("/api/**")
                        .filters(filter -> filter
                                .addRequestHeader(GATEWAY_HEADER, "vehicle-title-gateway")
                                .circuitBreaker(breaker -> breaker
                                        .setName("vehicleApi")
                                        .setFallbackUri("forward:/fallback/vehicle-api"))
                                .retry(retry -> retry
                                        .setRetries(2)
                                        .setMethods(HttpMethod.GET)
                                        .setStatuses(HttpStatus.BAD_GATEWAY, HttpStatus.SERVICE_UNAVAILABLE)))
                        .uri(vehicleApiUri))
                .route("vehicle-title-soap", route -> route
                        .path("/ws", "/ws/**")
                        .filters(filter -> filter
                                .addRequestHeader(GATEWAY_HEADER, "vehicle-title-gateway")
                                .circuitBreaker(breaker -> breaker
                                        .setName("vehicleApi")
                                        .setFallbackUri("forward:/fallback/vehicle-api")))
                        .uri(vehicleApiUri))
                .build();
    }
}

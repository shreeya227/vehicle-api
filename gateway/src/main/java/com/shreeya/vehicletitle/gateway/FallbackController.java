package com.shreeya.vehicletitle.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Returned when the Vehicle Title API is down or its circuit is open, so clients get a clear,
 * retryable 503 in RFC 9457 format instead of a hung connection.
 */
@RestController
class FallbackController {

    @RequestMapping("/fallback/vehicle-api")
    ResponseEntity<ProblemDetail> vehicleApiUnavailable() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The Vehicle Title API is temporarily unavailable. Retry later; submissions are safe "
                        + "to replay with the same Idempotency-Key.");
        problem.setTitle("Service unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "30")
                .body(problem);
    }
}

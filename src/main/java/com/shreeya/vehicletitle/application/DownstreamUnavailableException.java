package com.shreeya.vehicletitle.application;

/**
 * Thrown by an {@link OutboxPublisher} when a downstream system is known to be unavailable
 * (for example, its circuit breaker is open) and the call was not attempted.
 *
 * The dispatcher treats this differently from an ordinary delivery failure: the event keeps its
 * attempt count and stays pending, because nothing was actually tried.
 */
public class DownstreamUnavailableException extends RuntimeException {

    public DownstreamUnavailableException(String downstream, Throwable cause) {
        super(downstream + " is unavailable; delivery was not attempted", cause);
    }
}

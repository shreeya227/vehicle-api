package com.shreeya.vehicletitle.infrastructure.integration;

import com.shreeya.vehicletitle.application.OutboxPublisher;
import com.shreeya.vehicletitle.domain.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingOutboxPublisher implements OutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingOutboxPublisher.class);

    @Override
    public void publish(OutboxEvent event) {
        LOGGER.info("Published integration event id={} type={} aggregateId={} payload={}",
                event.getId(), event.getEventType(), event.getAggregateId(), event.getPayload());
    }
}

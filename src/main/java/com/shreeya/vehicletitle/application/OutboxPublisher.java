package com.shreeya.vehicletitle.application;

import com.shreeya.vehicletitle.domain.OutboxEvent;

public interface OutboxPublisher {

    void publish(OutboxEvent event);
}

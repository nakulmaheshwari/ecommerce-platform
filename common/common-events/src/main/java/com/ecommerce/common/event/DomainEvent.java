package com.ecommerce.common.event;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Getter
@SuperBuilder
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
public abstract class DomainEvent {
    private UUID eventId;
    private String eventType;
    private String version;
    private Instant occurredAt;
    private String correlationId;  // OpenTelemetry trace ID — follows the request everywhere
    private String causationId;    // ID of the event that caused this event

    // Called before publishing — ensures required fields are always set
    public DomainEvent init(String correlationId) {
        this.eventId = UUID.randomUUID();
        this.occurredAt = Instant.now();
        this.correlationId = correlationId;
        return this;
    }
}

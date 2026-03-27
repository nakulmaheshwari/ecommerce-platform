package com.ecommerce.common.event;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;
import java.util.UUID;

@Getter
@SuperBuilder
@NoArgsConstructor
@JsonTypeName("notification.triggered")
public class NotificationTriggeredEvent extends DomainEvent {
    public static final String TOPIC = "notification-triggered";
    public static final String VERSION = "1.0";

    private UUID userId;
    private String channel;        // EMAIL, SMS, PUSH
    private String templateId;     // e.g. "order-confirmed-v2"
    private Map<String, String> templateVars;
    private String recipientEmail;
    private String recipientPhone;
}

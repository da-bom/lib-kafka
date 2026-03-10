package com.dabom.messaging.kafka.event.dto.notification;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
        property = "subType",
        defaultImpl = Void.class)
@JsonSubTypes({
    @JsonSubTypes.Type(value = QuotaUpdatedPayload.class, name = NotificationSubTypes.QUOTA_UPDATED),
    @JsonSubTypes.Type(value = CustomerBlockedPayload.class, name = NotificationSubTypes.CUSTOMER_BLOCKED),
    @JsonSubTypes.Type(value = ThresholdAlertPayload.class, name = NotificationSubTypes.THRESHOLD_ALERT)
})
public sealed interface NotificationPayload
        permits QuotaUpdatedPayload, CustomerBlockedPayload, ThresholdAlertPayload {}

package com.dabom.messaging.kafka.event.dto.notification;

public record ThresholdAlertPayload(Long familyId, Integer thresholdPercent, String message)
        implements NotificationPayload {}

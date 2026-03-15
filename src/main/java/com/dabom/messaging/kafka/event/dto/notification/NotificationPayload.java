package com.dabom.messaging.kafka.event.dto.notification;

import java.util.Map;

public record NotificationPayload(
        Long familyId,
        Long customerId,
        NotificationType type,
        String title,
        String message,
        Map<String, Object> data) {}

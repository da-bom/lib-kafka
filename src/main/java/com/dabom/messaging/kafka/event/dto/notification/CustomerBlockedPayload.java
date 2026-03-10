package com.dabom.messaging.kafka.event.dto.notification;

public record CustomerBlockedPayload(
        Long familyId, Long customerId, String blockReason, String blockedAt)
        implements NotificationPayload {}

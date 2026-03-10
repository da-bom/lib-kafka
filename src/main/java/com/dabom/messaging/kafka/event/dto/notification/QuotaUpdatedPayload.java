package com.dabom.messaging.kafka.event.dto.notification;

public record QuotaUpdatedPayload(
        Long familyId,
        Long customerId,
        Long familyRemainingBytes,
        Double familyUsedPercent,
        Long customerUsedBytesCurrentMonth)
        implements NotificationPayload {}

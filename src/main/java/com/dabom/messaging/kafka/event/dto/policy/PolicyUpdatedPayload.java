package com.dabom.messaging.kafka.event.dto.policy;

public record PolicyUpdatedPayload(
        Long familyId,
        Long targetCustomerId,
        String policyKey,
        String newValue,
        boolean isActive) {}

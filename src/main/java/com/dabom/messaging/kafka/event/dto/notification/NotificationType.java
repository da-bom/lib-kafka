package com.dabom.messaging.kafka.event.dto.notification;

public enum NotificationType {
    THRESHOLD_ALERT,
    BLOCKED,
    UNBLOCKED,
    POLICY_CHANGED,
    MISSION_CREATED,
    REWARD_REQUESTED,
    REWARD_APPROVED,
    REWARD_REJECTED,
    APPEAL_CREATED,
    APPEAL_APPROVED,
    APPEAL_REJECTED,
    EMERGENCY_APPROVED
}

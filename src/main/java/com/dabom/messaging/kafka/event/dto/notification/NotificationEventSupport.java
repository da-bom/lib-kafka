package com.dabom.messaging.kafka.event.dto.notification;

import com.dabom.messaging.kafka.contract.KafkaEventTypes;
import com.dabom.messaging.kafka.event.dto.EventEnvelope;

public final class NotificationEventSupport {
    private NotificationEventSupport() {}

    public static EventEnvelope<NotificationPayload> toEnvelope(NotificationPayload payload) {
        return EventEnvelope.of(KafkaEventTypes.NOTIFICATION, payload);
    }

    public static String resolveTitle(NotificationType type) {
        return switch (type) {
            case QUOTA_UPDATED       -> "데이터 잔여량 갱신";
            case THRESHOLD_ALERT     -> "데이터 경고";
            case CUSTOMER_BLOCKED    -> "데이터 차단";
            case CUSTOMER_UNBLOCKED  -> "데이터 차단 해제";
            case POLICY_CHANGED      -> "정책 변경";
            case MISSION_CREATED     -> "미션 생성";
            case REWARD_REQUESTED    -> "보상 요청";
            case REWARD_APPROVED     -> "보상 승인";
            case REWARD_REJECTED     -> "보상 거절";
            case APPEAL_CREATED      -> "이의제기 접수";
            case APPEAL_APPROVED     -> "이의제기 승인";
            case APPEAL_REJECTED     -> "이의제기 거절";
            case EMERGENCY_APPROVED  -> "긴급 쿼터 승인";
        };
    }
}
